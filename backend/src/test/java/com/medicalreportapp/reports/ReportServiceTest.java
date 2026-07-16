package com.medicalreportapp.reports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.medicalreportapp.observations.DefaultUserProvider;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private DefaultUserProvider defaultUserProvider;

    @InjectMocks
    private ReportService reportService;

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
