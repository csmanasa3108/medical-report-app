package com.medicalreportapp.reports;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
class ReportUploadProcessingService {

    private final ReportService reportService;
    private final ParsedObservationService parsedObservationService;

    ReportUploadProcessingService(ReportService reportService, ParsedObservationService parsedObservationService) {
        this.reportService = reportService;
        this.parsedObservationService = parsedObservationService;
    }

    ReportResponse uploadAndProcess(UploadReportRequest request) {
        ReportResponse uploadedReport = reportService.upload(request);
        UUID reportId = uploadedReport.id();

        try {
            reportService.extractText(reportId);
        } catch (RuntimeException exception) {
            reportService.markExtractionFailed(reportId);
            throw processingFailed("Report uploaded, but text extraction failed", exception);
        }

        try {
            parsedObservationService.parse(reportId);
        } catch (RuntimeException exception) {
            reportService.markProcessingFailed(reportId);
            throw processingFailed("Report uploaded and text extracted, but observation parsing failed", exception);
        }

        return reportService.findById(reportId);
    }

    private static ResponseStatusException processingFailed(String reason, RuntimeException exception) {
        return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, reason, exception);
    }
}
