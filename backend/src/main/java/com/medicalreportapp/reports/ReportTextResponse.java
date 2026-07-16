package com.medicalreportapp.reports;

import java.time.Instant;
import java.util.UUID;

public record ReportTextResponse(
    UUID reportId,
    String extractionStatus,
    Instant extractedAt,
    String extractedText
) {

    static ReportTextResponse from(Report report) {
        return new ReportTextResponse(
            report.getId(),
            report.getExtractionStatus(),
            report.getExtractedAt(),
            report.getExtractedText()
        );
    }
}
