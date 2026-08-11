package com.medicalreportapp.reports;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

interface ParsedObservationReviewProjection {

    UUID getParsedObservationId();

    UUID getReportId();

    String getReportOriginalFilename();

    String getLabName();

    LocalDate getReportDate();

    UUID getTestId();

    String getTestName();

    String getRawTestName();

    LocalDate getObservedAt();

    String getValueText();

    BigDecimal getNumericValue();

    String getUnit();

    String getReferenceRange();

    ParsedObservationStatus getStatus();

    Instant getCreatedAt();
}
