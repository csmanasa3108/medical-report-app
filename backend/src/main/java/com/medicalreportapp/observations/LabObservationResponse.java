package com.medicalreportapp.observations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record LabObservationResponse(
    UUID id,
    UUID testId,
    String testName,
    LocalDate observedAt,
    BigDecimal numericValue,
    String unit,
    BigDecimal referenceLow,
    BigDecimal referenceHigh,
    String abnormalFlag
) {

    static LabObservationResponse from(LabObservation observation, String testName) {
        return new LabObservationResponse(
            observation.getId(),
            observation.getTestId(),
            testName,
            observation.getObservedAt(),
            observation.getNumericValue(),
            observation.getUnit(),
            observation.getReferenceLow(),
            observation.getReferenceHigh(),
            observation.getAbnormalFlag()
        );
    }
}
