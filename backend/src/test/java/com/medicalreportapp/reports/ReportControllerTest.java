package com.medicalreportapp.reports;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

@WebMvcTest(ReportController.class)
class ReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReportService reportService;

    @MockitoBean
    private ParsedObservationService parsedObservationService;

    @Test
    void createReturnsCreatedReport() throws Exception {
        UUID reportId = UUID.fromString("33333333-3333-3333-3333-333333333333");

        when(reportService.create(any(CreateReportRequest.class))).thenReturn(new ReportResponse(
            reportId,
            "lab-report-july.pdf",
            LocalDate.parse("2026-07-09"),
            "Quest Diagnostics",
            null,
            null,
            null,
            null,
            null,
            null,
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
    void uploadReturnsCreatedReport() throws Exception {
        UUID reportId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "lab-report-july.pdf",
            "application/pdf",
            "%PDF-1.7 test".getBytes()
        );

        when(reportService.upload(any(UploadReportRequest.class))).thenReturn(new ReportResponse(
            reportId,
            "lab-report-july.pdf",
            LocalDate.parse("2026-07-09"),
            "Quest Diagnostics",
            reportId + ".pdf",
            "uploads/reports/" + reportId + ".pdf",
            "application/pdf",
            13L,
            null,
            null,
            "UPLOADED",
            Instant.parse("2026-07-10T12:00:00Z")
        ));

        mockMvc.perform(multipart("/api/reports/upload")
                .file(file)
                .param("reportDate", "2026-07-09")
                .param("labName", "Quest Diagnostics"))
            .andExpect(status().isCreated())
            .andExpect(content().contentTypeCompatibleWith("application/json"))
            .andExpect(jsonPath("$.id").value(reportId.toString()))
            .andExpect(jsonPath("$.originalFilename").value("lab-report-july.pdf"))
            .andExpect(jsonPath("$.reportDate").value("2026-07-09"))
            .andExpect(jsonPath("$.labName").value("Quest Diagnostics"))
            .andExpect(jsonPath("$.storedFilename").value(reportId + ".pdf"))
            .andExpect(jsonPath("$.storagePath").value("uploads/reports/" + reportId + ".pdf"))
            .andExpect(jsonPath("$.contentType").value("application/pdf"))
            .andExpect(jsonPath("$.fileSizeBytes").value(13))
            .andExpect(jsonPath("$.status").value("UPLOADED"));

        verify(reportService).upload(any(UploadReportRequest.class));
    }

    @Test
    void uploadReturnsBadRequestForRejectedFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "lab-report.txt",
            "text/plain",
            "not a pdf".getBytes()
        );

        when(reportService.upload(any(UploadReportRequest.class)))
            .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only PDF uploads are supported"));

        mockMvc.perform(multipart("/api/reports/upload").file(file))
            .andExpect(status().isBadRequest());
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
            new ReportResponse(newestReportId, "new.pdf", LocalDate.parse("2026-07-10"), "Quest Diagnostics", null, null, null, null, null, null, "CREATED", Instant.parse("2026-07-10T12:00:00Z")),
            new ReportResponse(olderReportId, "old.pdf", LocalDate.parse("2026-07-09"), "Labcorp", null, null, null, null, null, null, "CREATED", Instant.parse("2026-07-09T12:00:00Z"))
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
            null,
            null,
            null,
            null,
            null,
            null,
            "CREATED",
            Instant.parse("2026-07-10T12:00:00Z")
        ));

        mockMvc.perform(get("/api/reports/{reportId}", reportId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(reportId.toString()))
            .andExpect(jsonPath("$.originalFilename").value("lab-report-july.pdf"));
    }

    @Test
    void extractTextReturnsExtractedText() throws Exception {
        UUID reportId = UUID.fromString("33333333-3333-3333-3333-333333333333");

        when(reportService.extractText(reportId)).thenReturn(new ReportTextResponse(
            reportId,
            "TEXT_EXTRACTED",
            Instant.parse("2026-07-10T13:00:00Z"),
            "Hemoglobin 13.4 g/dL"
        ));

        mockMvc.perform(post("/api/reports/{reportId}/extract-text", reportId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.reportId").value(reportId.toString()))
            .andExpect(jsonPath("$.extractionStatus").value("TEXT_EXTRACTED"))
            .andExpect(jsonPath("$.extractedAt").value("2026-07-10T13:00:00Z"))
            .andExpect(jsonPath("$.extractedText").value("Hemoglobin 13.4 g/dL"));
    }

    @Test
    void findTextReturnsExtractedText() throws Exception {
        UUID reportId = UUID.fromString("33333333-3333-3333-3333-333333333333");

        when(reportService.findText(reportId)).thenReturn(new ReportTextResponse(
            reportId,
            "TEXT_EXTRACTED",
            Instant.parse("2026-07-10T13:00:00Z"),
            "Hemoglobin 13.4 g/dL"
        ));

        mockMvc.perform(get("/api/reports/{reportId}/text", reportId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.reportId").value(reportId.toString()))
            .andExpect(jsonPath("$.extractionStatus").value("TEXT_EXTRACTED"))
            .andExpect(jsonPath("$.extractedText").value("Hemoglobin 13.4 g/dL"));
    }

    @Test
    void parseObservationsReturnsParsedRows() throws Exception {
        UUID reportId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID parsedObservationId = UUID.fromString("55555555-5555-5555-5555-555555555555");
        UUID matchedTestId = UUID.fromString("11111111-1111-1111-1111-111111111111");

        when(parsedObservationService.parse(reportId)).thenReturn(List.of(new ParsedObservationResponse(
            parsedObservationId,
            reportId,
            "Hemoglobin",
            matchedTestId,
            LocalDate.parse("2026-07-09"),
            "12.8",
            new java.math.BigDecimal("12.8000"),
            "g/dL",
            "12.0 - 15.5",
            "NEEDS_REVIEW",
            Instant.parse("2026-07-10T14:00:00Z")
        )));

        mockMvc.perform(post("/api/reports/{reportId}/parse-observations", reportId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(parsedObservationId.toString()))
            .andExpect(jsonPath("$[0].reportId").value(reportId.toString()))
            .andExpect(jsonPath("$[0].rawTestName").value("Hemoglobin"))
            .andExpect(jsonPath("$[0].matchedTestId").value(matchedTestId.toString()))
            .andExpect(jsonPath("$[0].observedAt").value("2026-07-09"))
            .andExpect(jsonPath("$[0].rawValue").value("12.8"))
            .andExpect(jsonPath("$[0].numericValue").value(12.8000))
            .andExpect(jsonPath("$[0].unit").value("g/dL"))
            .andExpect(jsonPath("$[0].referenceRange").value("12.0 - 15.5"))
            .andExpect(jsonPath("$[0].status").value("NEEDS_REVIEW"))
            .andExpect(jsonPath("$[0].createdAt").value("2026-07-10T14:00:00Z"));
    }

    @Test
    void findParsedObservationsReturnsStoredParsedRows() throws Exception {
        UUID reportId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID parsedObservationId = UUID.fromString("55555555-5555-5555-5555-555555555555");

        when(parsedObservationService.findByReportId(reportId)).thenReturn(List.of(new ParsedObservationResponse(
            parsedObservationId,
            reportId,
            "Vitamin D",
            null,
            LocalDate.parse("2026-07-09"),
            "24",
            new java.math.BigDecimal("24.0000"),
            "ng/mL",
            "30 - 100",
            "NEEDS_REVIEW",
            Instant.parse("2026-07-10T14:00:00Z")
        )));

        mockMvc.perform(get("/api/reports/{reportId}/parsed-observations", reportId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(parsedObservationId.toString()))
            .andExpect(jsonPath("$[0].reportId").value(reportId.toString()))
            .andExpect(jsonPath("$[0].rawTestName").value("Vitamin D"))
            .andExpect(jsonPath("$[0].matchedTestId").doesNotExist())
            .andExpect(jsonPath("$[0].status").value("NEEDS_REVIEW"));
    }

    @Test
    void findByIdReturnsNotFoundForUnknownReport() throws Exception {
        UUID reportId = UUID.fromString("33333333-3333-3333-3333-333333333333");

        when(reportService.findById(reportId)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found"));

        mockMvc.perform(get("/api/reports/{reportId}", reportId))
            .andExpect(status().isNotFound());
    }
}
