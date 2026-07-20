package com.medicalreportapp.reports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.medicalreportapp.observations.DefaultUserProvider;
import com.medicalreportapp.testcatalog.TestCatalogLookupService;
import com.medicalreportapp.testcatalog.TestCatalogMatch;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ParsedObservationServiceTest {

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private ParsedObservationRepository parsedObservationRepository;

    @Mock
    private TestCatalogLookupService testCatalogLookupService;

    @Mock
    private DefaultUserProvider defaultUserProvider;

    private ParsedObservationService parsedObservationService;

    @BeforeEach
    void setUp() {
        parsedObservationService = new ParsedObservationService(
            reportRepository,
            parsedObservationRepository,
            new ParsedObservationParser(),
            testCatalogLookupService,
            defaultUserProvider
        );
    }

    @Test
    void parseStoresSimpleLabRowsAsNeedsReview() {
        UUID userId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID reportId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID hemoglobinId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID wbcId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        Report report = report(reportId, userId);
        report.markTextExtracted("""
            Hemoglobin 12.8 g/dL 12.0 - 15.5
            WBC 6.4 10^3/uL 4.0 - 11.0
            this line is not a result
            Vitamin D 24 ng/mL 30 - 100
            """, Instant.parse("2026-07-10T13:00:00Z"));

        when(defaultUserProvider.getDefaultUserId()).thenReturn(userId);
        when(reportRepository.findByIdAndUserId(reportId, userId)).thenReturn(Optional.of(report));
        when(testCatalogLookupService.findAllForMatching()).thenReturn(List.of(
            new TestCatalogMatch(hemoglobinId, "hemoglobin", "Hemoglobin", "g/dL"),
            new TestCatalogMatch(wbcId, "wbc", "WBC", "10^3/uL")
        ));
        when(parsedObservationRepository.saveAll(org.mockito.ArgumentMatchers.anyList()))
            .thenAnswer(invocation -> invocation.getArgument(0));

        List<ParsedObservationResponse> responses = parsedObservationService.parse(reportId);

        ArgumentCaptor<List<ParsedObservation>> observationsCaptor = ArgumentCaptor.forClass(List.class);
        verify(parsedObservationRepository).deleteByReportId(reportId);
        verify(parsedObservationRepository).saveAll(observationsCaptor.capture());

        List<ParsedObservation> savedObservations = observationsCaptor.getValue();
        assertThat(savedObservations).hasSize(3);
        assertThat(savedObservations).extracting(ParsedObservation::getReportId).containsOnly(reportId);
        assertThat(savedObservations).extracting(ParsedObservation::getRawTestName)
            .containsExactly("Hemoglobin", "WBC", "Vitamin D");
        assertThat(savedObservations).extracting(ParsedObservation::getStatus)
            .containsOnly(ParsedObservationStatus.NEEDS_REVIEW);
        assertThat(savedObservations.get(0).getMatchedTestId()).isEqualTo(hemoglobinId);
        assertThat(savedObservations.get(1).getMatchedTestId()).isEqualTo(wbcId);
        assertThat(savedObservations.get(2).getMatchedTestId()).isNull();
        assertThat(savedObservations.get(0).getObservedAt()).isEqualTo(LocalDate.parse("2026-07-09"));
        assertThat(savedObservations.get(1).getUnit()).isEqualTo("10^3/uL");
        assertThat(savedObservations.get(2).getReferenceRange()).isEqualTo("30 - 100");

        assertThat(responses).extracting(ParsedObservationResponse::rawTestName)
            .containsExactly("Hemoglobin", "WBC", "Vitamin D");
    }

    @Test
    void parseRejectsReportWithoutExtractedText() {
        UUID userId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID reportId = UUID.fromString("33333333-3333-3333-3333-333333333333");

        when(defaultUserProvider.getDefaultUserId()).thenReturn(userId);
        when(reportRepository.findByIdAndUserId(reportId, userId)).thenReturn(Optional.of(report(reportId, userId)));

        assertThatThrownBy(() -> parsedObservationService.parse(reportId))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void findByReportIdRequiresReportForDefaultUser() {
        UUID userId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID reportId = UUID.fromString("33333333-3333-3333-3333-333333333333");

        when(defaultUserProvider.getDefaultUserId()).thenReturn(userId);
        when(reportRepository.findByIdAndUserId(reportId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> parsedObservationService.findByReportId(reportId))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
    }

    private static Report report(UUID reportId, UUID userId) {
        return new Report(
            reportId,
            userId,
            "lab-report-july.pdf",
            LocalDate.parse("2026-07-09"),
            "Quest Diagnostics",
            null,
            null,
            null,
            null,
            ReportStatus.CREATED
        );
    }
}
