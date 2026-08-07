package com.medicalreportapp.observations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record LabObservationTrendPointResponse(
    LocalDate observedAt,
    BigDecimal numericValue,
    String unit,
    LabObservationSourceType sourceType,
    UUID reportId,
    String reportOriginalFilename,
    String labName,
    LocalDate reportDate,
    UUID parsedObservationId
) {
}
