package com.medicalreportapp.reports;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "parsed_observations")
class ParsedObservation {

    @Id
    private UUID id;

    @Column(name = "report_id", nullable = false)
    private UUID reportId;

    @Column(name = "raw_test_name", nullable = false, length = 255)
    private String rawTestName;

    @Column(name = "matched_test_id")
    private UUID matchedTestId;

    @Column(name = "observed_at")
    private LocalDate observedAt;

    @Column(name = "raw_value", length = 100)
    private String rawValue;

    @Column(name = "numeric_value", precision = 12, scale = 4)
    private BigDecimal numericValue;

    @Column(name = "unit", length = 50)
    private String unit;

    @Column(name = "reference_range", length = 255)
    private String referenceRange;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private ParsedObservationStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "confirmed_observation_id")
    private UUID confirmedObservationId;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    protected ParsedObservation() {
    }

    ParsedObservation(
        UUID id,
        UUID reportId,
        String rawTestName,
        UUID matchedTestId,
        LocalDate observedAt,
        String rawValue,
        BigDecimal numericValue,
        String unit,
        String referenceRange,
        ParsedObservationStatus status
    ) {
        this.id = id;
        this.reportId = reportId;
        this.rawTestName = rawTestName;
        this.matchedTestId = matchedTestId;
        this.observedAt = observedAt;
        this.rawValue = rawValue;
        this.numericValue = numericValue;
        this.unit = unit;
        this.referenceRange = referenceRange;
        this.status = status;
    }

    UUID getId() {
        return id;
    }

    UUID getReportId() {
        return reportId;
    }

    String getRawTestName() {
        return rawTestName;
    }

    UUID getMatchedTestId() {
        return matchedTestId;
    }

    LocalDate getObservedAt() {
        return observedAt;
    }

    String getRawValue() {
        return rawValue;
    }

    BigDecimal getNumericValue() {
        return numericValue;
    }

    String getUnit() {
        return unit;
    }

    String getReferenceRange() {
        return referenceRange;
    }

    ParsedObservationStatus getStatus() {
        return status;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    UUID getConfirmedObservationId() {
        return confirmedObservationId;
    }

    Instant getConfirmedAt() {
        return confirmedAt;
    }

    void setRawTestName(String rawTestName) {
        this.rawTestName = rawTestName;
    }

    void setMatchedTestId(UUID matchedTestId) {
        this.matchedTestId = matchedTestId;
    }

    void setObservedAt(LocalDate observedAt) {
        this.observedAt = observedAt;
    }

    void setRawValue(String rawValue) {
        this.rawValue = rawValue;
    }

    void setNumericValue(BigDecimal numericValue) {
        this.numericValue = numericValue;
    }

    void setUnit(String unit) {
        this.unit = unit;
    }

    void setReferenceRange(String referenceRange) {
        this.referenceRange = referenceRange;
    }

    void markConfirmed() {
        this.status = ParsedObservationStatus.CONFIRMED;
    }

    void markConfirmed(UUID confirmedObservationId, Instant confirmedAt) {
        this.status = ParsedObservationStatus.CONFIRMED;
        this.confirmedObservationId = confirmedObservationId;
        this.confirmedAt = confirmedAt;
    }

    void markRejected() {
        this.status = ParsedObservationStatus.REJECTED;
    }
}
