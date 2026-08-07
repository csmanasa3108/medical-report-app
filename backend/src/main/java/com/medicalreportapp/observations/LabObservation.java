package com.medicalreportapp.observations;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "lab_observations")
class LabObservation {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "test_id", nullable = false)
    private UUID testId;

    @Column(name = "observed_at", nullable = false)
    private LocalDate observedAt;

    @Column(name = "numeric_value", nullable = false, precision = 12, scale = 4)
    private BigDecimal numericValue;

    @Column(name = "unit", nullable = false, length = 50)
    private String unit;

    @Column(name = "reference_low", nullable = false, precision = 12, scale = 4)
    private BigDecimal referenceLow;

    @Column(name = "reference_high", nullable = false, precision = 12, scale = 4)
    private BigDecimal referenceHigh;

    @Column(name = "abnormal_flag", nullable = false, length = 20)
    private String abnormalFlag;

    @Column(name = "source_report_id")
    private UUID sourceReportId;

    @Column(name = "source_parsed_observation_id")
    private UUID sourceParsedObservationId;

    protected LabObservation() {
    }

    LabObservation(
        UUID id,
        UUID userId,
        UUID testId,
        LocalDate observedAt,
        BigDecimal numericValue,
        String unit,
        BigDecimal referenceLow,
        BigDecimal referenceHigh,
        String abnormalFlag
    ) {
        this(
            id,
            userId,
            testId,
            observedAt,
            numericValue,
            unit,
            referenceLow,
            referenceHigh,
            abnormalFlag,
            null,
            null
        );
    }

    LabObservation(
        UUID id,
        UUID userId,
        UUID testId,
        LocalDate observedAt,
        BigDecimal numericValue,
        String unit,
        BigDecimal referenceLow,
        BigDecimal referenceHigh,
        String abnormalFlag,
        UUID sourceReportId,
        UUID sourceParsedObservationId
    ) {
        this.id = id;
        this.userId = userId;
        this.testId = testId;
        this.observedAt = observedAt;
        this.numericValue = numericValue;
        this.unit = unit;
        this.referenceLow = referenceLow;
        this.referenceHigh = referenceHigh;
        this.abnormalFlag = abnormalFlag;
        this.sourceReportId = sourceReportId;
        this.sourceParsedObservationId = sourceParsedObservationId;
    }

    UUID getId() {
        return id;
    }

    UUID getUserId() {
        return userId;
    }

    UUID getTestId() {
        return testId;
    }

    LocalDate getObservedAt() {
        return observedAt;
    }

    BigDecimal getNumericValue() {
        return numericValue;
    }

    String getUnit() {
        return unit;
    }

    BigDecimal getReferenceLow() {
        return referenceLow;
    }

    BigDecimal getReferenceHigh() {
        return referenceHigh;
    }

    String getAbnormalFlag() {
        return abnormalFlag;
    }

    UUID getSourceReportId() {
        return sourceReportId;
    }

    UUID getSourceParsedObservationId() {
        return sourceParsedObservationId;
    }
}
