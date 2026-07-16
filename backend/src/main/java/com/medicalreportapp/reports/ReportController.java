package com.medicalreportapp.reports;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ReportController {

    private final ReportService reportService;

    ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @PostMapping("/api/reports")
    @ResponseStatus(HttpStatus.CREATED)
    public ReportResponse create(@Valid @RequestBody CreateReportRequest request) {
        return reportService.create(request);
    }

    @GetMapping("/api/reports")
    public List<ReportResponse> findAll() {
        return reportService.findAll();
    }

    @GetMapping("/api/reports/{reportId}")
    public ReportResponse findById(@PathVariable UUID reportId) {
        return reportService.findById(reportId);
    }
}
