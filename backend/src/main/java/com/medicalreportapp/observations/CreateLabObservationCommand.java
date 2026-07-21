package com.medicalreportapp.observations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateLabObservationCommand(
    UUID testId,
    LocalDate observedAt,
    BigDecimal numericValue,
    String unit,
    BigDecimal referenceLow,
    BigDecimal referenceHigh,
    String abnormalFlag
) {

    static CreateLabObservationCommand from(CreateLabObservationRequest request) {
        return new CreateLabObservationCommand(
            request.testId(),
            request.observedAt(),
            request.numericValue(),
            request.unit(),
            request.referenceLow(),
            request.referenceHigh(),
            request.abnormalFlag()
        );
    }
}
