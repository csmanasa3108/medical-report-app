package com.medicalreportapp.reports;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "reports")
class Report {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "report_date")
    private LocalDate reportDate;

    @Column(name = "lab_name", length = 255)
    private String labName;

    @Column(name = "stored_filename", length = 255)
    private String storedFilename;

    @Column(name = "storage_path")
    private String storagePath;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private ReportStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Report() {
    }

    Report(
        UUID id,
        UUID userId,
        String originalFilename,
        LocalDate reportDate,
        String labName,
        String storedFilename,
        String storagePath,
        String contentType,
        Long fileSizeBytes,
        ReportStatus status
    ) {
        this.id = id;
        this.userId = userId;
        this.originalFilename = originalFilename;
        this.reportDate = reportDate;
        this.labName = labName;
        this.storedFilename = storedFilename;
        this.storagePath = storagePath;
        this.contentType = contentType;
        this.fileSizeBytes = fileSizeBytes;
        this.status = status;
    }

    UUID getId() {
        return id;
    }

    UUID getUserId() {
        return userId;
    }

    String getOriginalFilename() {
        return originalFilename;
    }

    LocalDate getReportDate() {
        return reportDate;
    }

    String getLabName() {
        return labName;
    }

    String getStoredFilename() {
        return storedFilename;
    }

    String getStoragePath() {
        return storagePath;
    }

    String getContentType() {
        return contentType;
    }

    Long getFileSizeBytes() {
        return fileSizeBytes;
    }

    ReportStatus getStatus() {
        return status;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
