package com.medicalreportapp.reports;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record CreateReportRequest(
    @NotBlank @Size(max = 255) String originalFilename,
    LocalDate reportDate,
    @Size(max = 255) String labName
) {
}
