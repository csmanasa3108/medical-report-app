package com.medicalreportapp.reports;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ParsedObservationResponse(
    UUID id,
    UUID reportId,
    String rawTestName,
    UUID matchedTestId,
    LocalDate observedAt,
    String rawValue,
    BigDecimal numericValue,
    String unit,
    String referenceRange,
    String status,
    Instant createdAt
) {

    static ParsedObservationResponse from(ParsedObservation observation) {
        return new ParsedObservationResponse(
            observation.getId(),
            observation.getReportId(),
            observation.getRawTestName(),
            observation.getMatchedTestId(),
            observation.getObservedAt(),
            observation.getRawValue(),
            observation.getNumericValue(),
            observation.getUnit(),
            observation.getReferenceRange(),
            observation.getStatus().name(),
            observation.getCreatedAt()
        );
    }
}
