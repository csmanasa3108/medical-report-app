package com.medicalreportapp.reports;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ReportResponse(
    UUID id,
    String originalFilename,
    LocalDate reportDate,
    String labName,
    String storedFilename,
    String storagePath,
    String contentType,
    Long fileSizeBytes,
    String status,
    Instant createdAt
) {

    static ReportResponse from(Report report) {
        return new ReportResponse(
            report.getId(),
            report.getOriginalFilename(),
            report.getReportDate(),
            report.getLabName(),
            report.getStoredFilename(),
            report.getStoragePath(),
            report.getContentType(),
            report.getFileSizeBytes(),
            report.getStatus().name(),
            report.getCreatedAt()
        );
    }
}
