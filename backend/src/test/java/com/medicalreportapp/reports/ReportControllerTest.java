package com.medicalreportapp.reports;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import org.springframework.http.HttpStatus;

@WebMvcTest(ReportController.class)
class ReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReportService reportService;

    @Test
    void createReturnsCreatedReport() throws Exception {
        UUID reportId = UUID.fromString("33333333-3333-3333-3333-333333333333");

        when(reportService.create(any(CreateReportRequest.class))).thenReturn(new ReportResponse(
            reportId,
            "lab-report-july.pdf",
            LocalDate.parse("2026-07-09"),
            "Quest Diagnostics",
            "CREATED",
            Instant.parse("2026-07-10T12:00:00Z")
        ));

        mockMvc.perform(post("/api/reports")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "originalFilename": "lab-report-july.pdf",
                      "reportDate": "2026-07-09",
                      "labName": "Quest Diagnostics"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(content().contentTypeCompatibleWith("application/json"))
            .andExpect(jsonPath("$.id").value(reportId.toString()))
            .andExpect(jsonPath("$.originalFilename").value("lab-report-july.pdf"))
            .andExpect(jsonPath("$.reportDate").value("2026-07-09"))
            .andExpect(jsonPath("$.labName").value("Quest Diagnostics"))
            .andExpect(jsonPath("$.status").value("CREATED"))
            .andExpect(jsonPath("$.createdAt").value("2026-07-10T12:00:00Z"));

        verify(reportService).create(any(CreateReportRequest.class));
    }

    @Test
    void createRejectsMissingOriginalFilename() throws Exception {
        mockMvc.perform(post("/api/reports")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "reportDate": "2026-07-09",
                      "labName": "Quest Diagnostics"
                    }
                    """))
            .andExpect(status().isBadRequest());
    }

    @Test
    void findAllReturnsReports() throws Exception {
        UUID newestReportId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID olderReportId = UUID.fromString("44444444-4444-4444-4444-444444444444");

        when(reportService.findAll()).thenReturn(List.of(
            new ReportResponse(newestReportId, "new.pdf", LocalDate.parse("2026-07-10"), "Quest Diagnostics", "CREATED", Instant.parse("2026-07-10T12:00:00Z")),
            new ReportResponse(olderReportId, "old.pdf", LocalDate.parse("2026-07-09"), "Labcorp", "CREATED", Instant.parse("2026-07-09T12:00:00Z"))
        ));

        mockMvc.perform(get("/api/reports"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("application/json"))
            .andExpect(jsonPath("$[0].id").value(newestReportId.toString()))
            .andExpect(jsonPath("$[0].originalFilename").value("new.pdf"))
            .andExpect(jsonPath("$[1].id").value(olderReportId.toString()))
            .andExpect(jsonPath("$[1].originalFilename").value("old.pdf"));
    }

    @Test
    void findByIdReturnsReport() throws Exception {
        UUID reportId = UUID.fromString("33333333-3333-3333-3333-333333333333");

        when(reportService.findById(reportId)).thenReturn(new ReportResponse(
            reportId,
            "lab-report-july.pdf",
            LocalDate.parse("2026-07-09"),
            "Quest Diagnostics",
            "CREATED",
            Instant.parse("2026-07-10T12:00:00Z")
        ));

        mockMvc.perform(get("/api/reports/{reportId}", reportId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(reportId.toString()))
            .andExpect(jsonPath("$.originalFilename").value("lab-report-july.pdf"));
    }

    @Test
    void findByIdReturnsNotFoundForUnknownReport() throws Exception {
        UUID reportId = UUID.fromString("33333333-3333-3333-3333-333333333333");

        when(reportService.findById(reportId)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found"));

        mockMvc.perform(get("/api/reports/{reportId}", reportId))
            .andExpect(status().isNotFound());
    }
}
