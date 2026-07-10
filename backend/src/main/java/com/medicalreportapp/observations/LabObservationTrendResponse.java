package com.medicalreportapp.observations;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record LabObservationTrendResponse(
    UUID testId,
    String testName,
    String unit,
    List<LabObservationTrendPointResponse> points,
    BigDecimal latestValue,
    BigDecimal previousValue,
    BigDecimal absoluteChange,
    BigDecimal percentChange
) {
}
