package com.medicalreportapp.observations;

import com.medicalreportapp.testcatalog.TestCatalogLookup;
import com.medicalreportapp.testcatalog.TestCatalogLookupService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class LabObservationService {

    private final LabObservationRepository labObservationRepository;
    private final TestCatalogLookupService testCatalogLookupService;
    private final DefaultUserProvider defaultUserProvider;

    LabObservationService(
        LabObservationRepository labObservationRepository,
        TestCatalogLookupService testCatalogLookupService,
        DefaultUserProvider defaultUserProvider
    ) {
        this.labObservationRepository = labObservationRepository;
        this.testCatalogLookupService = testCatalogLookupService;
        this.defaultUserProvider = defaultUserProvider;
    }

    @Transactional
    public LabObservationResponse create(CreateLabObservationRequest request) {
        return create(CreateLabObservationCommand.from(request));
    }

    @Transactional
    public LabObservationResponse create(CreateLabObservationCommand command) {
        TestCatalogLookup test = testCatalogLookupService.findById(command.testId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Test catalog entry not found"));
        String unit = StringUtils.hasText(command.unit()) ? command.unit() : test.defaultUnit();
        if (!StringUtils.hasText(unit)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lab observation has no unit");
        }
        UUID patientUserId = command.patientUserId() != null ? command.patientUserId() : defaultUserProvider.requireCurrentUserCanWritePatientData();
        defaultUserProvider.requireCurrentUserCanWritePatientData(patientUserId);
        UUID createdByUserId = command.createdByUserId() != null ? command.createdByUserId() : defaultUserProvider.getCurrentUserId();

        Optional<LabObservation> existingObservation = findExistingObservation(command, patientUserId, test.id(), unit);
        if (existingObservation.isPresent()) {
            return toResponse(existingObservation.get());
        }

        LabObservation observation = new LabObservation(
            UUID.randomUUID(),
            patientUserId,
            patientUserId,
            createdByUserId,
            test.id(),
            command.observedAt(),
            command.numericValue(),
            unit,
            command.referenceLow(),
            command.referenceHigh(),
            command.abnormalFlag(),
            command.sourceReportId(),
            command.sourceParsedObservationId()
        );

        LabObservation savedObservation = labObservationRepository.save(observation);
        return LabObservationResponse.from(savedObservation, test.displayName());
    }

    @Transactional(readOnly = true)
    public Optional<LabObservationResponse> findByIdForDefaultUser(UUID observationId) {
        return labObservationRepository.findByIdAndPatientUserId(observationId, defaultUserProvider.resolveReadablePatientId(null))
            .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Optional<LabObservationResponse> findBySourceParsedObservationIdForDefaultUser(UUID sourceParsedObservationId) {
        return labObservationRepository.findFirstByPatientUserIdAndSourceParsedObservationIdOrderByIdAsc(
                defaultUserProvider.resolveReadablePatientId(null),
                sourceParsedObservationId
            )
            .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public LabObservationTrendResponse trend(UUID testId) {
        return trend(testId, null);
    }

    @Transactional(readOnly = true)
    public LabObservationTrendResponse trend(UUID testId, UUID requestedPatientId) {
        TestCatalogLookup test = testCatalogLookupService.findById(testId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Test catalog entry not found"));
        UUID patientUserId = defaultUserProvider.resolveReadablePatientId(requestedPatientId);

        List<LabObservationTrendPointProjection> observations = labObservationRepository.findTrendPointsByPatientUserIdAndTestId(
            patientUserId,
            test.id()
        );

        List<LabObservationTrendPointResponse> points = observations.stream()
            .map(observation -> new LabObservationTrendPointResponse(
                observation.getObservedAt(),
                observation.getNumericValue(),
                observation.getUnit(),
                isReportSource(observation) ? LabObservationSourceType.REPORT : LabObservationSourceType.MANUAL,
                observation.getReportId(),
                observation.getReportOriginalFilename(),
                observation.getLabName(),
                observation.getReportDate(),
                observation.getParsedObservationId()
            ))
            .toList();

        if (observations.isEmpty()) {
            return new LabObservationTrendResponse(
                test.id(),
                test.displayName(),
                test.defaultUnit(),
                points,
                null,
                null,
                null,
                null
            );
        }

        LabObservationTrendPointProjection latest = observations.getLast();
        BigDecimal latestValue = latest.getNumericValue();

        if (observations.size() == 1) {
            return new LabObservationTrendResponse(
                test.id(),
                test.displayName(),
                latest.getUnit(),
                points,
                latestValue,
                null,
                null,
                null
            );
        }

        BigDecimal previousValue = observations.get(observations.size() - 2).getNumericValue();
        BigDecimal absoluteChange = latestValue.subtract(previousValue);
        BigDecimal percentChange = null;
        if (previousValue.compareTo(BigDecimal.ZERO) != 0) {
            percentChange = absoluteChange
                .multiply(new BigDecimal("100"))
                .divide(previousValue, 4, RoundingMode.HALF_UP);
        }

        return new LabObservationTrendResponse(
            test.id(),
            test.displayName(),
            latest.getUnit(),
            points,
            latestValue,
            previousValue,
            absoluteChange,
            percentChange
        );
    }

    private static boolean isReportSource(LabObservationTrendPointProjection observation) {
        return observation.getReportId() != null || observation.getParsedObservationId() != null;
    }

    private Optional<LabObservation> findExistingObservation(
        CreateLabObservationCommand command,
        UUID patientUserId,
        UUID testId,
        String unit
    ) {
        if (command.sourceParsedObservationId() != null) {
            return labObservationRepository.findFirstByPatientUserIdAndSourceParsedObservationIdOrderByIdAsc(
                patientUserId,
                command.sourceParsedObservationId()
            );
        }

        if (command.sourceReportId() == null) {
            return labObservationRepository.findFirstByPatientUserIdAndTestIdAndObservedAtAndNumericValueAndUnitAndSourceReportIdIsNullAndSourceParsedObservationIdIsNullOrderByIdAsc(
                patientUserId,
                testId,
                command.observedAt(),
                command.numericValue(),
                unit
            );
        }

        return Optional.empty();
    }

    private LabObservationResponse toResponse(LabObservation observation) {
        TestCatalogLookup test = testCatalogLookupService.findById(observation.getTestId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Confirmed lab observation has no matching test"));
        return LabObservationResponse.from(observation, test.displayName());
    }
}
