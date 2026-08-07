package com.medicalreportapp.observations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

interface LabObservationTrendPointProjection {

    LocalDate getObservedAt();

    BigDecimal getNumericValue();

    String getUnit();

    UUID getReportId();

    String getReportOriginalFilename();

    String getLabName();

    LocalDate getReportDate();

    UUID getParsedObservationId();
}
