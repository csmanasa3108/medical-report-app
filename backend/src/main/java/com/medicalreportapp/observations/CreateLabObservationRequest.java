package com.medicalreportapp.observations;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateLabObservationRequest(
    @NotNull UUID testId,
    @NotNull LocalDate observedAt,
    @NotNull BigDecimal numericValue,
    @NotBlank String unit,
    @NotNull BigDecimal referenceLow,
    @NotNull BigDecimal referenceHigh,
    @NotBlank String abnormalFlag
) {
}
