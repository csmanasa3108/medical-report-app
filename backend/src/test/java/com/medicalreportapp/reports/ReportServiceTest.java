package com.medicalreportapp.reports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.medicalreportapp.observations.DefaultUserProvider;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private DefaultUserProvider defaultUserProvider;

    @TempDir
    private Path reportUploadDirectory;

    private ReportService reportService;

    @BeforeEach
    void setUp() {
        reportService = new ReportService(reportRepository, defaultUserProvider, reportUploadDirectory.toString());
    }

    @Test
    void createSavesReportForDefaultUserWithCreatedStatus() {
        UUID userId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        CreateReportRequest request = new CreateReportRequest(
            "lab-report-july.pdf",
            LocalDate.parse("2026-07-09"),
            "Quest Diagnostics"
        );

        when(defaultUserProvider.getDefaultUserId()).thenReturn(userId);
        when(reportRepository.saveAndFlush(any(Report.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ReportResponse response = reportService.create(request);

        ArgumentCaptor<Report> reportCaptor = ArgumentCaptor.forClass(Report.class);
        verify(reportRepository).saveAndFlush(reportCaptor.capture());

        Report savedReport = reportCaptor.getValue();
        assertThat(savedReport.getId()).isNotNull();
        assertThat(savedReport.getUserId()).isEqualTo(userId);
        assertThat(savedReport.getOriginalFilename()).isEqualTo("lab-report-july.pdf");
        assertThat(savedReport.getReportDate()).isEqualTo(LocalDate.parse("2026-07-09"));
        assertThat(savedReport.getLabName()).isEqualTo("Quest Diagnostics");
        assertThat(savedReport.getStatus()).isEqualTo(ReportStatus.CREATED);

        assertThat(response.id()).isEqualTo(savedReport.getId());
        assertThat(response.originalFilename()).isEqualTo("lab-report-july.pdf");
        assertThat(response.reportDate()).isEqualTo(LocalDate.parse("2026-07-09"));
        assertThat(response.labName()).isEqualTo("Quest Diagnostics");
        assertThat(response.status()).isEqualTo("CREATED");
    }

    @Test
    void uploadStoresPdfAndSavesUploadedReportMetadata() throws Exception {
        UUID userId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "lab-report-july.pdf",
            "application/pdf",
            "%PDF-1.7 test".getBytes()
        );
        UploadReportRequest request = new UploadReportRequest(
            file,
            LocalDate.parse("2026-07-09"),
            "Quest Diagnostics"
        );

        when(defaultUserProvider.getDefaultUserId()).thenReturn(userId);
        when(reportRepository.saveAndFlush(any(Report.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ReportResponse response = reportService.upload(request);

        ArgumentCaptor<Report> reportCaptor = ArgumentCaptor.forClass(Report.class);
        verify(reportRepository).saveAndFlush(reportCaptor.capture());

        Report savedReport = reportCaptor.getValue();
        assertThat(savedReport.getId()).isNotNull();
        assertThat(savedReport.getUserId()).isEqualTo(userId);
        assertThat(savedReport.getOriginalFilename()).isEqualTo("lab-report-july.pdf");
        assertThat(savedReport.getReportDate()).isEqualTo(LocalDate.parse("2026-07-09"));
        assertThat(savedReport.getLabName()).isEqualTo("Quest Diagnostics");
        assertThat(savedReport.getStatus()).isEqualTo(ReportStatus.UPLOADED);
        assertThat(savedReport.getStoredFilename()).isEqualTo(savedReport.getId() + ".pdf");
        assertThat(savedReport.getStoragePath()).isEqualTo(reportUploadDirectory.resolve(savedReport.getStoredFilename()).toString());
        assertThat(savedReport.getContentType()).isEqualTo("application/pdf");
        assertThat(savedReport.getFileSizeBytes()).isEqualTo(file.getSize());
        assertThat(Files.readString(reportUploadDirectory.resolve(savedReport.getStoredFilename()))).isEqualTo("%PDF-1.7 test");

        assertThat(response.status()).isEqualTo("UPLOADED");
        assertThat(response.storedFilename()).isEqualTo(savedReport.getStoredFilename());
        assertThat(response.storagePath()).isEqualTo(savedReport.getStoragePath());
        assertThat(response.contentType()).isEqualTo("application/pdf");
        assertThat(response.fileSizeBytes()).isEqualTo(file.getSize());
    }

    @Test
    void uploadRejectsEmptyFile() {
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "empty.pdf",
            "application/pdf",
            new byte[0]
        );

        assertThatThrownBy(() -> reportService.upload(new UploadReportRequest(file, null, null)))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void uploadRejectsNonPdfFile() {
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "lab-report.txt",
            "text/plain",
            "not a pdf".getBytes()
        );

        assertThatThrownBy(() -> reportService.upload(new UploadReportRequest(file, null, null)))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void findAllReturnsReportsForDefaultUserInRepositoryOrder() {
        UUID userId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        Report newestReport = report("33333333-3333-3333-3333-333333333333", userId, "new.pdf", "2026-07-10");
        Report olderReport = report("44444444-4444-4444-4444-444444444444", userId, "old.pdf", "2026-07-09");

        when(defaultUserProvider.getDefaultUserId()).thenReturn(userId);
        when(reportRepository.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(newestReport, olderReport));

        List<ReportResponse> responses = reportService.findAll();

        assertThat(responses).extracting(ReportResponse::originalFilename)
            .containsExactly("new.pdf", "old.pdf");
    }

    @Test
    void findByIdReturnsReportForDefaultUser() {
        UUID userId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID reportId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        Report report = report(reportId.toString(), userId, "lab-report-july.pdf", "2026-07-09");

        when(defaultUserProvider.getDefaultUserId()).thenReturn(userId);
        when(reportRepository.findByIdAndUserId(reportId, userId)).thenReturn(Optional.of(report));

        ReportResponse response = reportService.findById(reportId);

        assertThat(response.id()).isEqualTo(reportId);
        assertThat(response.originalFilename()).isEqualTo("lab-report-july.pdf");
    }

    @Test
    void findByIdThrowsNotFoundWhenReportDoesNotExistForDefaultUser() {
        UUID userId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID reportId = UUID.fromString("33333333-3333-3333-3333-333333333333");

        when(defaultUserProvider.getDefaultUserId()).thenReturn(userId);
        when(reportRepository.findByIdAndUserId(reportId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reportService.findById(reportId))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("404 NOT_FOUND");
    }

    private static Report report(String reportId, UUID userId, String originalFilename, String reportDate) {
        Report report = new Report(
            UUID.fromString(reportId),
            userId,
            originalFilename,
            LocalDate.parse(reportDate),
            "Quest Diagnostics",
            null,
            null,
            null,
            null,
            ReportStatus.CREATED
        );
        setCreatedAt(report, Instant.parse(reportDate + "T12:00:00Z"));
        return report;
    }

    private static void setCreatedAt(Report report, Instant createdAt) {
        try {
            java.lang.reflect.Field field = Report.class.getDeclaredField("createdAt");
            field.setAccessible(true);
            field.set(report, createdAt);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
