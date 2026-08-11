package com.medicalreportapp.reports;

import com.medicalreportapp.testcatalog.TestCatalogLookup;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record ParsedObservationReviewResponse(
    UUID parsedObservationId,
    UUID reportId,
    String reportOriginalFilename,
    String labName,
    LocalDate reportDate,
    UUID testId,
    String testName,
    LocalDate observedAt,
    String valueText,
    BigDecimal numericValue,
    String unit,
    String referenceRange,
    String abnormalFlag,
    String status,
    Instant createdAt
) {

    private static final Pattern REFERENCE_RANGE = Pattern.compile(
        "^\\s*[<>]?(?<low>\\d+(?:\\.\\d+)?)\\s*-\\s*[<>]?(?<high>\\d+(?:\\.\\d+)?).*$"
    );

    static ParsedObservationReviewResponse from(ParsedObservationReviewProjection projection) {
        return new ParsedObservationReviewResponse(
            projection.getParsedObservationId(),
            projection.getReportId(),
            projection.getReportOriginalFilename(),
            projection.getLabName(),
            projection.getReportDate(),
            projection.getTestId(),
            projection.getTestName(),
            projection.getObservedAt(),
            projection.getValueText(),
            projection.getNumericValue(),
            projection.getUnit(),
            projection.getReferenceRange(),
            abnormalFlag(projection.getNumericValue(), projection.getReferenceRange()),
            projection.getStatus().name(),
            projection.getCreatedAt()
        );
    }

    static ParsedObservationReviewResponse from(
        ParsedObservation observation,
        Report report,
        TestCatalogLookup testCatalogLookup
    ) {
        return new ParsedObservationReviewResponse(
            observation.getId(),
            report.getId(),
            report.getOriginalFilename(),
            report.getLabName(),
            report.getReportDate(),
            observation.getMatchedTestId(),
            testCatalogLookup == null ? observation.getRawTestName() : testCatalogLookup.displayName(),
            observation.getObservedAt(),
            observation.getRawValue(),
            observation.getNumericValue(),
            observation.getUnit(),
            observation.getReferenceRange(),
            abnormalFlag(observation.getNumericValue(), observation.getReferenceRange()),
            observation.getStatus().name(),
            observation.getCreatedAt()
        );
    }

    private static String abnormalFlag(BigDecimal numericValue, String referenceRange) {
        if (numericValue == null) {
            return "unknown";
        }

        Matcher matcher = REFERENCE_RANGE.matcher(referenceRange == null ? "" : referenceRange);
        if (!matcher.matches()) {
            return "unknown";
        }

        BigDecimal low = new BigDecimal(matcher.group("low"));
        BigDecimal high = new BigDecimal(matcher.group("high"));
        if (numericValue.compareTo(low) < 0 || numericValue.compareTo(high) > 0) {
            return "abnormal";
        }
        return "normal";
    }
}
