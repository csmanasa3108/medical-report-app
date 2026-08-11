package com.medicalreportapp.reports;

import com.medicalreportapp.audit.AuditService;
import com.medicalreportapp.observations.DefaultUserProvider;
import com.medicalreportapp.observations.CreateLabObservationCommand;
import com.medicalreportapp.observations.LabObservationResponse;
import com.medicalreportapp.observations.LabObservationService;
import com.medicalreportapp.testcatalog.TestCatalogLookupService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
class ParsedObservationService {

    private static final Pattern REFERENCE_RANGE = Pattern.compile(
        "^\\s*[<>]?(?<low>\\d+(?:\\.\\d+)?)\\s*-\\s*[<>]?(?<high>\\d+(?:\\.\\d+)?).*$"
    );

    private final ReportRepository reportRepository;
    private final ParsedObservationRepository parsedObservationRepository;
    private final ParsedObservationParser parsedObservationParser;
    private final TestCatalogLookupService testCatalogLookupService;
    private final LabObservationService labObservationService;
    private final DefaultUserProvider defaultUserProvider;
    private final AuditService auditService;

    ParsedObservationService(
        ReportRepository reportRepository,
        ParsedObservationRepository parsedObservationRepository,
        ParsedObservationParser parsedObservationParser,
        TestCatalogLookupService testCatalogLookupService,
        LabObservationService labObservationService,
        DefaultUserProvider defaultUserProvider,
        AuditService auditService
    ) {
        this.reportRepository = reportRepository;
        this.parsedObservationRepository = parsedObservationRepository;
        this.parsedObservationParser = parsedObservationParser;
        this.testCatalogLookupService = testCatalogLookupService;
        this.labObservationService = labObservationService;
        this.defaultUserProvider = defaultUserProvider;
        this.auditService = auditService;
    }

    @Transactional
    public List<ParsedObservationResponse> parse(UUID reportId) {
        Report report = findReportForCurrentUserForWrite(reportId);
        if (!StringUtils.hasText(report.getExtractedText())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Report has no extracted text");
        }

        parsedObservationRepository.deleteByReportIdAndStatus(report.getId(), ParsedObservationStatus.NEEDS_REVIEW);
        parsedObservationRepository.flush();
        List<ParsedObservation> preservedObservations = parsedObservationRepository.findByReportIdAndStatusInOrderByCreatedAtAsc(
            report.getId(),
            List.of(ParsedObservationStatus.CONFIRMED, ParsedObservationStatus.REJECTED)
        );
        List<ParsedObservation> parsedObservations = parsedObservationParser.parse(
            report.getExtractedText(),
            report.getReportDate(),
            testCatalogLookupService.findAllForMatching()
        ).stream()
            .map(observation -> new ParsedObservation(
                observation.getId(),
                report.getId(),
                observation.getRawTestName(),
                observation.getMatchedTestId(),
                observation.getObservedAt(),
                observation.getRawValue(),
                observation.getNumericValue(),
                observation.getUnit(),
                observation.getReferenceRange(),
                observation.getStatus()
            ))
            .toList();
        List<ParsedObservation> uniqueParsedObservations = deduplicateParsedObservations(parsedObservations, preservedObservations);

        if (!uniqueParsedObservations.isEmpty()) {
            parsedObservationRepository.saveAllAndFlush(uniqueParsedObservations);
        }

        return parsedObservationRepository.findByReportIdOrderByCreatedAtAsc(report.getId()).stream()
            .map(ParsedObservationResponse::from)
            .toList();
    }

    @Transactional
    public List<ParsedObservationResponse> findByReportId(UUID reportId) {
        Report report = findReportForCurrentUserForRead(reportId);
        cleanupDuplicateNeedsReviewObservations(report.getId());
        return parsedObservationRepository.findByReportIdOrderByCreatedAtAsc(report.getId()).stream()
            .map(ParsedObservationResponse::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<ParsedObservationReviewResponse> findReviewQueue(UUID requestedPatientId, ParsedObservationStatus status) {
        UUID patientUserId = defaultUserProvider.resolveReadablePatientId(requestedPatientId);
        return deduplicateReviewQueue(parsedObservationRepository.findReviewQueueByPatientUserIdAndStatus(patientUserId, status)).stream()
            .map(ParsedObservationReviewResponse::from)
            .toList();
    }

    @Transactional
    public ParsedObservationResponse update(UUID parsedObservationId, UpdateParsedObservationRequest request) {
        ParsedObservation parsedObservation = parsedObservationRepository.findById(parsedObservationId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Parsed observation not found"));
        findReportForCurrentUserForWrite(parsedObservation.getReportId());

        if (parsedObservation.getStatus() == ParsedObservationStatus.CONFIRMED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Confirmed parsed observations cannot be edited");
        }

        if (request.hasRawTestName()) {
            if (!StringUtils.hasText(request.rawTestName())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Raw test name is required");
            }
            parsedObservation.setRawTestName(request.rawTestName());
        }
        if (request.hasMatchedTestId()) {
            if (request.matchedTestId() != null && testCatalogLookupService.findById(request.matchedTestId()).isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Matched test does not exist");
            }
            parsedObservation.setMatchedTestId(request.matchedTestId());
        }
        if (request.hasObservedAt()) {
            parsedObservation.setObservedAt(request.observedAt());
        }
        if (request.hasRawValue()) {
            parsedObservation.setRawValue(request.rawValue());
        }
        if (request.hasNumericValue()) {
            parsedObservation.setNumericValue(request.numericValue());
        }
        if (request.hasUnit()) {
            parsedObservation.setUnit(request.unit());
        }
        if (request.hasReferenceRange()) {
            parsedObservation.setReferenceRange(request.referenceRange());
        }

        return ParsedObservationResponse.from(parsedObservation);
    }

    @Transactional
    public LabObservationResponse confirm(UUID parsedObservationId) {
        ParsedObservation parsedObservation = parsedObservationRepository.findByIdForUpdate(parsedObservationId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Parsed observation not found"));
        Report report = findReportForCurrentUserForWrite(parsedObservation.getReportId());

        if (parsedObservation.getStatus() == ParsedObservationStatus.CONFIRMED) {
            if (parsedObservation.getConfirmedObservationId() == null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Parsed observation is already confirmed without a linked lab observation");
            }
            return labObservationService.findByIdForDefaultUser(parsedObservation.getConfirmedObservationId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Confirmed lab observation not found"));
        }
        Optional<LabObservationResponse> existingSourceObservation =
            labObservationService.findBySourceParsedObservationIdForDefaultUser(parsedObservation.getId());
        if (existingSourceObservation.isPresent()) {
            LabObservationResponse response = existingSourceObservation.get();
            parsedObservation.markConfirmed(response.id(), Instant.now());
            auditParsedObservationConfirmed(report, parsedObservation, response.id());
            return response;
        }
        if (parsedObservation.getMatchedTestId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Parsed observation has no matched test");
        }
        if (parsedObservation.getNumericValue() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Parsed observation has no numeric value");
        }

        LocalDate observedAt = parsedObservation.getObservedAt() != null
            ? parsedObservation.getObservedAt()
            : report.getReportDate();
        if (observedAt == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Parsed observation has no observation date or report date");
        }

        ReferenceBounds referenceBounds = parseReferenceBounds(parsedObservation.getReferenceRange());
        LabObservationResponse response = labObservationService.create(new CreateLabObservationCommand(
            parsedObservation.getMatchedTestId(),
            observedAt,
            parsedObservation.getNumericValue(),
            parsedObservation.getUnit(),
            referenceBounds.low(),
            referenceBounds.high(),
            abnormalFlag(parsedObservation.getNumericValue(), referenceBounds),
            report.getId(),
            parsedObservation.getId(),
            report.getPatientUserId(),
            defaultUserProvider.getCurrentUserId()
        ));

        parsedObservation.markConfirmed(response.id(), Instant.now());
        auditParsedObservationConfirmed(report, parsedObservation, response.id());
        return response;
    }

    @Transactional
    public ParsedObservationReviewResponse reject(UUID parsedObservationId) {
        ParsedObservation parsedObservation = parsedObservationRepository.findByIdForUpdate(parsedObservationId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Parsed observation not found"));
        Report report = findReportForCurrentUserForWrite(parsedObservation.getReportId());

        if (parsedObservation.getStatus() == ParsedObservationStatus.CONFIRMED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Confirmed parsed observations cannot be rejected");
        }
        if (parsedObservation.getStatus() != ParsedObservationStatus.REJECTED) {
            parsedObservation.markRejected();
            auditParsedObservationRejected(report, parsedObservation);
        }

        return ParsedObservationReviewResponse.from(
            parsedObservation,
            report,
            parsedObservation.getMatchedTestId() == null
                ? null
                : testCatalogLookupService.findById(parsedObservation.getMatchedTestId()).orElse(null)
        );
    }

    private void auditParsedObservationConfirmed(Report report, ParsedObservation parsedObservation, UUID labObservationId) {
        auditService.record(
            "PARSED_OBSERVATION_CONFIRMED",
            report.getPatientUserId(),
            "PARSED_OBSERVATION",
            parsedObservation.getId(),
            "{\"reportId\":\"" + report.getId() + "\",\"labObservationId\":\"" + labObservationId + "\"}"
        );
    }

    private void auditParsedObservationRejected(Report report, ParsedObservation parsedObservation) {
        auditService.record(
            "PARSED_OBSERVATION_REJECTED",
            report.getPatientUserId(),
            "PARSED_OBSERVATION",
            parsedObservation.getId(),
            "{\"reportId\":\"" + report.getId() + "\"}"
        );
    }

    private Report findReportForCurrentUserForRead(UUID reportId) {
        Report report = reportRepository.findById(reportId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found"));
        defaultUserProvider.requireCurrentUserCanReadPatientData(report.getPatientUserId());
        return report;
    }

    private Report findReportForCurrentUserForWrite(UUID reportId) {
        Report report = reportRepository.findByIdForUpdate(reportId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found"));
        defaultUserProvider.requireCurrentUserCanWritePatientData(report.getPatientUserId());
        return report;
    }

    private static List<ParsedObservation> deduplicateParsedObservations(
        List<ParsedObservation> candidates,
        List<ParsedObservation> confirmedObservations
    ) {
        Map<ParsedObservationDeduplicationKey, ParsedObservation> uniqueObservations = new LinkedHashMap<>();
        for (ParsedObservation confirmedObservation : confirmedObservations) {
            uniqueObservations.put(ParsedObservationDeduplicationKey.from(confirmedObservation), confirmedObservation);
        }

        for (ParsedObservation candidate : candidates) {
            uniqueObservations.putIfAbsent(ParsedObservationDeduplicationKey.from(candidate), candidate);
        }

        return uniqueObservations.values().stream()
            .filter(observation -> observation.getStatus() == ParsedObservationStatus.NEEDS_REVIEW)
            .toList();
    }

    private void cleanupDuplicateNeedsReviewObservations(UUID reportId) {
        List<ParsedObservation> needsReviewObservations =
            parsedObservationRepository.findByReportIdAndStatusOrderByCreatedAtAsc(reportId, ParsedObservationStatus.NEEDS_REVIEW);
        Map<ParsedObservationDeduplicationKey, ParsedObservation> firstObservationByKey = new LinkedHashMap<>();
        List<ParsedObservation> duplicateObservations = new ArrayList<>();

        for (ParsedObservation observation : needsReviewObservations) {
            ParsedObservation existingObservation = firstObservationByKey.putIfAbsent(
                ParsedObservationDeduplicationKey.from(observation),
                observation
            );
            if (existingObservation != null) {
                duplicateObservations.add(observation);
            }
        }

        if (!duplicateObservations.isEmpty()) {
            parsedObservationRepository.deleteAllInBatch(duplicateObservations);
            parsedObservationRepository.flush();
        }
    }

    private static List<ParsedObservationReviewProjection> deduplicateReviewQueue(
        List<ParsedObservationReviewProjection> observations
    ) {
        Set<ParsedObservationDeduplicationKey> seenKeys = new HashSet<>();
        return observations.stream()
            .filter(observation -> seenKeys.add(ParsedObservationDeduplicationKey.from(observation)))
            .toList();
    }

    private static ReferenceBounds parseReferenceBounds(String referenceRange) {
        if (!StringUtils.hasText(referenceRange)) {
            return ReferenceBounds.unknown();
        }

        Matcher matcher = REFERENCE_RANGE.matcher(referenceRange);
        if (!matcher.matches()) {
            return ReferenceBounds.unknown();
        }

        return new ReferenceBounds(
            new BigDecimal(matcher.group("low")),
            new BigDecimal(matcher.group("high"))
        );
    }

    private static String abnormalFlag(BigDecimal numericValue, ReferenceBounds referenceBounds) {
        if (referenceBounds.isUnknown()) {
            return "unknown";
        }
        if (numericValue.compareTo(referenceBounds.low()) < 0 || numericValue.compareTo(referenceBounds.high()) > 0) {
            return "abnormal";
        }
        return "normal";
    }

    private record ReferenceBounds(BigDecimal low, BigDecimal high) {

        private static ReferenceBounds unknown() {
            return new ReferenceBounds(BigDecimal.ZERO, BigDecimal.ZERO);
        }

        private boolean isUnknown() {
            return BigDecimal.ZERO.compareTo(low) == 0 && BigDecimal.ZERO.compareTo(high) == 0;
        }
    }

    private record ParsedObservationDeduplicationKey(
        UUID matchedTestId,
        UUID reportId,
        String rawTestName,
        LocalDate observedAt,
        String rawValue,
        BigDecimal numericValue,
        String unit
    ) {

        private static ParsedObservationDeduplicationKey from(ParsedObservation observation) {
            UUID matchedTestId = observation.getMatchedTestId();
            return new ParsedObservationDeduplicationKey(
                matchedTestId,
                observation.getReportId(),
                matchedTestId == null ? normalizeRawTestName(observation.getRawTestName()) : null,
                observation.getObservedAt(),
                normalizeValueText(observation.getRawValue()),
                observation.getNumericValue(),
                trimToNull(observation.getUnit())
            );
        }

        private static ParsedObservationDeduplicationKey from(ParsedObservationReviewProjection observation) {
            UUID matchedTestId = observation.getTestId();
            return new ParsedObservationDeduplicationKey(
                matchedTestId,
                observation.getReportId(),
                matchedTestId == null ? normalizeRawTestName(observation.getRawTestName()) : null,
                observation.getObservedAt(),
                normalizeValueText(observation.getValueText()),
                observation.getNumericValue(),
                trimToNull(observation.getUnit())
            );
        }

        private static String normalizeRawTestName(String rawTestName) {
            String trimmedValue = trimToNull(rawTestName);
            return trimmedValue == null ? null : trimmedValue.toLowerCase(Locale.ROOT);
        }

        private static String normalizeValueText(String valueText) {
            String trimmedValue = trimToNull(valueText);
            if (trimmedValue == null) {
                return null;
            }

            try {
                return new BigDecimal(trimmedValue).stripTrailingZeros().toPlainString();
            } catch (NumberFormatException exception) {
                return trimmedValue.toLowerCase(Locale.ROOT);
            }
        }

        private static String trimToNull(String value) {
            if (value == null) {
                return null;
            }
            String trimmedValue = value.trim();
            return trimmedValue.isEmpty() ? null : trimmedValue;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof ParsedObservationDeduplicationKey otherKey)) {
                return false;
            }
            return Objects.equals(matchedTestId, otherKey.matchedTestId)
                && Objects.equals(reportId, otherKey.reportId)
                && Objects.equals(rawTestName, otherKey.rawTestName)
                && Objects.equals(observedAt, otherKey.observedAt)
                && Objects.equals(rawValue, otherKey.rawValue)
                && sameNumericValue(numericValue, otherKey.numericValue)
                && Objects.equals(unit, otherKey.unit);
        }

        @Override
        public int hashCode() {
            return Objects.hash(matchedTestId, reportId, rawTestName, observedAt, rawValue, numericValueHash(), unit);
        }

        private boolean sameNumericValue(BigDecimal left, BigDecimal right) {
            if (left == null || right == null) {
                return left == right;
            }
            return left.compareTo(right) == 0;
        }

        private int numericValueHash() {
            return numericValue == null ? 0 : numericValue.stripTrailingZeros().hashCode();
        }
    }
}
