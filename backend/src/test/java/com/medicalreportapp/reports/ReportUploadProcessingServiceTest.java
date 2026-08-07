package com.medicalreportapp.reports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ReportUploadProcessingServiceTest {

    @Mock
    private ReportService reportService;

    @Mock
    private ParsedObservationService parsedObservationService;

    private ReportUploadProcessingService reportUploadProcessingService;

    @BeforeEach
    void setUp() {
        reportUploadProcessingService = new ReportUploadProcessingService(reportService, parsedObservationService);
    }

    @Test
    void uploadAndProcessExtractsTextParsesObservationsAndReturnsUpdatedReport() {
        UUID reportId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UploadReportRequest request = uploadRequest();
        ReportResponse uploadedResponse = uploadedResponse(reportId);
        ReportResponse processedResponse = processedResponse(reportId);

        when(reportService.upload(request)).thenReturn(uploadedResponse);
        when(reportService.extractText(reportId)).thenReturn(new ReportTextResponse(
            reportId,
            "TEXT_EXTRACTED",
            Instant.parse("2026-07-10T13:00:00Z"),
            "Hemoglobin 13.4 g/dL"
        ));
        when(parsedObservationService.parse(reportId)).thenReturn(List.of());
        when(reportService.findById(reportId)).thenReturn(processedResponse);

        ReportResponse response = reportUploadProcessingService.uploadAndProcess(request);

        assertThat(response).isEqualTo(processedResponse);
        verify(reportService).upload(request);
        verify(reportService).extractText(reportId);
        verify(parsedObservationService).parse(reportId);
        verify(reportService).findById(reportId);
    }

    @Test
    void uploadAndProcessMarksExtractionFailedWhenExtractionFailsAfterUpload() {
        UUID reportId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UploadReportRequest request = uploadRequest();

        when(reportService.upload(request)).thenReturn(uploadedResponse(reportId));
        when(reportService.extractText(reportId)).thenThrow(new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "Could not extract text from stored PDF"
        ));

        assertThatThrownBy(() -> reportUploadProcessingService.uploadAndProcess(request))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
            .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        verify(reportService).markExtractionFailed(reportId);
        verify(parsedObservationService, never()).parse(reportId);
        verify(reportService, never()).markProcessingFailed(reportId);
    }

    @Test
    void uploadAndProcessMarksProcessingFailedWhenParsingFailsAfterExtraction() {
        UUID reportId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UploadReportRequest request = uploadRequest();

        when(reportService.upload(request)).thenReturn(uploadedResponse(reportId));
        when(reportService.extractText(reportId)).thenReturn(new ReportTextResponse(
            reportId,
            "TEXT_EXTRACTED",
            Instant.parse("2026-07-10T13:00:00Z"),
            "Hemoglobin 13.4 g/dL"
        ));
        when(parsedObservationService.parse(reportId)).thenThrow(new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "Report has no extracted text"
        ));

        assertThatThrownBy(() -> reportUploadProcessingService.uploadAndProcess(request))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
            .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        verify(reportService).markProcessingFailed(reportId);
        verify(reportService, never()).markExtractionFailed(reportId);
    }

    private static UploadReportRequest uploadRequest() {
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "lab-report-july.pdf",
            "application/pdf",
            "%PDF-1.7 test".getBytes()
        );
        return new UploadReportRequest(file, LocalDate.parse("2026-07-09"), "Quest Diagnostics");
    }

    private static ReportResponse uploadedResponse(UUID reportId) {
        return new ReportResponse(
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
        );
    }

    private static ReportResponse processedResponse(UUID reportId) {
        return new ReportResponse(
            reportId,
            "lab-report-july.pdf",
            LocalDate.parse("2026-07-09"),
            "Quest Diagnostics",
            reportId + ".pdf",
            "uploads/reports/" + reportId + ".pdf",
            "application/pdf",
            13L,
            "TEXT_EXTRACTED",
            Instant.parse("2026-07-10T13:00:00Z"),
            "TEXT_EXTRACTED",
            Instant.parse("2026-07-10T12:00:00Z")
        );
    }
}
