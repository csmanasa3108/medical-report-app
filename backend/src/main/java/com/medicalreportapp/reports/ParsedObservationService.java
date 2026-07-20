package com.medicalreportapp.reports;

import com.medicalreportapp.observations.DefaultUserProvider;
import com.medicalreportapp.testcatalog.TestCatalogLookupService;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
class ParsedObservationService {

    private final ReportRepository reportRepository;
    private final ParsedObservationRepository parsedObservationRepository;
    private final ParsedObservationParser parsedObservationParser;
    private final TestCatalogLookupService testCatalogLookupService;
    private final DefaultUserProvider defaultUserProvider;

    ParsedObservationService(
        ReportRepository reportRepository,
        ParsedObservationRepository parsedObservationRepository,
        ParsedObservationParser parsedObservationParser,
        TestCatalogLookupService testCatalogLookupService,
        DefaultUserProvider defaultUserProvider
    ) {
        this.reportRepository = reportRepository;
        this.parsedObservationRepository = parsedObservationRepository;
        this.parsedObservationParser = parsedObservationParser;
        this.testCatalogLookupService = testCatalogLookupService;
        this.defaultUserProvider = defaultUserProvider;
    }

    @Transactional
    public List<ParsedObservationResponse> parse(UUID reportId) {
        Report report = findReportForDefaultUser(reportId);
        if (!StringUtils.hasText(report.getExtractedText())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Report has no extracted text");
        }

        parsedObservationRepository.deleteByReportId(report.getId());
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

        return parsedObservationRepository.saveAll(parsedObservations).stream()
            .map(ParsedObservationResponse::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<ParsedObservationResponse> findByReportId(UUID reportId) {
        Report report = findReportForDefaultUser(reportId);
        return parsedObservationRepository.findByReportIdOrderByCreatedAtAsc(report.getId()).stream()
            .map(ParsedObservationResponse::from)
            .toList();
    }

    private Report findReportForDefaultUser(UUID reportId) {
        UUID userId = defaultUserProvider.getDefaultUserId();
        return reportRepository.findByIdAndUserId(reportId, userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found"));
    }
}
