package com.medicalreportapp.reports;

import com.medicalreportapp.observations.LabObservationResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
class ReportController {

    private final ReportService reportService;
    private final ParsedObservationService parsedObservationService;

    ReportController(ReportService reportService, ParsedObservationService parsedObservationService) {
        this.reportService = reportService;
        this.parsedObservationService = parsedObservationService;
    }

    @PostMapping("/api/reports")
    @ResponseStatus(HttpStatus.CREATED)
    public ReportResponse create(@Valid @RequestBody CreateReportRequest request) {
        return reportService.create(request);
    }

    @PostMapping("/api/reports/upload")
    @ResponseStatus(HttpStatus.CREATED)
    public ReportResponse upload(
        @RequestParam("file") MultipartFile file,
        @RequestParam(value = "reportDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate reportDate,
        @RequestParam(value = "labName", required = false) @Size(max = 255) String labName
    ) {
        return reportService.upload(new UploadReportRequest(file, reportDate, labName));
    }

    @GetMapping("/api/reports")
    public List<ReportResponse> findAll() {
        return reportService.findAll();
    }

    @GetMapping("/api/reports/{reportId}")
    public ReportResponse findById(@PathVariable UUID reportId) {
        return reportService.findById(reportId);
    }

    @PostMapping("/api/reports/{reportId}/extract-text")
    public ReportTextResponse extractText(@PathVariable UUID reportId) {
        return reportService.extractText(reportId);
    }

    @GetMapping("/api/reports/{reportId}/text")
    public ReportTextResponse findText(@PathVariable UUID reportId) {
        return reportService.findText(reportId);
    }

    @PostMapping("/api/reports/{reportId}/parse-observations")
    public List<ParsedObservationResponse> parseObservations(@PathVariable UUID reportId) {
        return parsedObservationService.parse(reportId);
    }

    @GetMapping("/api/reports/{reportId}/parsed-observations")
    public List<ParsedObservationResponse> findParsedObservations(@PathVariable UUID reportId) {
        return parsedObservationService.findByReportId(reportId);
    }

    @PatchMapping("/api/parsed-observations/{parsedObservationId}")
    public ParsedObservationResponse updateParsedObservation(
        @PathVariable UUID parsedObservationId,
        @Valid @RequestBody UpdateParsedObservationRequest request
    ) {
        return parsedObservationService.update(parsedObservationId, request);
    }

    @PostMapping("/api/parsed-observations/{parsedObservationId}/confirm")
    public LabObservationResponse confirmParsedObservation(@PathVariable UUID parsedObservationId) {
        return parsedObservationService.confirm(parsedObservationId);
    }
}
