package com.medicalreportapp.observations;

import java.math.BigDecimal;
import java.time.LocalDate;

public record LabObservationTrendPointResponse(
    LocalDate date,
    BigDecimal value
) {
}
