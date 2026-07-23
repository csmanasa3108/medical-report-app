package com.medicalreportapp.reports;

import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public class UpdateParsedObservationRequest {

    @Size(max = 255)
    private String rawTestName;
    private boolean rawTestNameSet;

    private UUID matchedTestId;
    private boolean matchedTestIdSet;

    private LocalDate observedAt;
    private boolean observedAtSet;

    @Size(max = 100)
    private String rawValue;
    private boolean rawValueSet;

    private BigDecimal numericValue;
    private boolean numericValueSet;

    @Size(max = 50)
    private String unit;
    private boolean unitSet;

    @Size(max = 255)
    private String referenceRange;
    private boolean referenceRangeSet;

    public String rawTestName() {
        return rawTestName;
    }

    public boolean hasRawTestName() {
        return rawTestNameSet;
    }

    @JsonSetter("rawTestName")
    public void setRawTestName(String rawTestName) {
        this.rawTestName = rawTestName;
        this.rawTestNameSet = true;
    }

    public UUID matchedTestId() {
        return matchedTestId;
    }

    public boolean hasMatchedTestId() {
        return matchedTestIdSet;
    }

    @JsonSetter("matchedTestId")
    public void setMatchedTestId(UUID matchedTestId) {
        this.matchedTestId = matchedTestId;
        this.matchedTestIdSet = true;
    }

    public LocalDate observedAt() {
        return observedAt;
    }

    public boolean hasObservedAt() {
        return observedAtSet;
    }

    @JsonSetter("observedAt")
    public void setObservedAt(LocalDate observedAt) {
        this.observedAt = observedAt;
        this.observedAtSet = true;
    }

    public String rawValue() {
        return rawValue;
    }

    public boolean hasRawValue() {
        return rawValueSet;
    }

    @JsonSetter("rawValue")
    public void setRawValue(String rawValue) {
        this.rawValue = rawValue;
        this.rawValueSet = true;
    }

    public BigDecimal numericValue() {
        return numericValue;
    }

    public boolean hasNumericValue() {
        return numericValueSet;
    }

    @JsonSetter("numericValue")
    public void setNumericValue(BigDecimal numericValue) {
        this.numericValue = numericValue;
        this.numericValueSet = true;
    }

    public String unit() {
        return unit;
    }

    public boolean hasUnit() {
        return unitSet;
    }

    @JsonSetter("unit")
    public void setUnit(String unit) {
        this.unit = unit;
        this.unitSet = true;
    }

    public String referenceRange() {
        return referenceRange;
    }

    public boolean hasReferenceRange() {
        return referenceRangeSet;
    }

    @JsonSetter("referenceRange")
    public void setReferenceRange(String referenceRange) {
        this.referenceRange = referenceRange;
        this.referenceRangeSet = true;
    }
}
