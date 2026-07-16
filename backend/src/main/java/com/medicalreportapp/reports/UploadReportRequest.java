package com.medicalreportapp.reports;

import java.time.LocalDate;

import org.springframework.web.multipart.MultipartFile;

record UploadReportRequest(
    MultipartFile file,
    LocalDate reportDate,
    String labName
) {
}
