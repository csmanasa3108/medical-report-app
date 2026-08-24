package com.medicalreportapp.reports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.medicalreportapp.audit.AuditService;
import com.medicalreportapp.observations.CreateLabObservationCommand;
import com.medicalreportapp.observations.DefaultUserProvider;
import com.medicalreportapp.observations.LabObservationResponse;
import com.medicalreportapp.observations.LabObservationService;
import com.medicalreportapp.testcatalog.TestCatalogLookup;
import com.medicalreportapp.testcatalog.TestCatalogLookupService;
import com.medicalreportapp.testcatalog.TestCatalogMatch;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

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
    private LabObservationService labObservationService;

    @Mock
    private DefaultUserProvider defaultUserProvider;

    @Mock
    private AuditService auditService;

    private ParsedObservationService parsedObservationService;

    @BeforeEach
    void setUp() {
        parsedObservationService = new ParsedObservationService(
            reportRepository,
            parsedObservationRepository,
            new ParsedObservationParser(),
            testCatalogLookupService,
            labObservationService,
            defaultUserProvider,
            auditService
        );
        lenient()
            .when(labObservationService.findBySourceParsedObservationIdForDefaultUser(any(UUID.class)))
            .thenReturn(Optional.empty());
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

        allowDefaultUserAccess(userId);
        when(reportRepository.findByIdForUpdate(reportId)).thenReturn(Optional.of(report));
        when(testCatalogLookupService.findAllForMatching()).thenReturn(List.of(
            new TestCatalogMatch(hemoglobinId, "hemoglobin", "Hemoglobin", "g/dL"),
            new TestCatalogMatch(wbcId, "wbc", "WBC", "10^3/uL")
        ));
        when(parsedObservationRepository.findByReportIdAndStatusInOrderByCreatedAtAsc(reportId, List.of(ParsedObservationStatus.CONFIRMED, ParsedObservationStatus.REJECTED)))
            .thenReturn(List.of());
        AtomicReference<List<ParsedObservation>> storedObservations = new AtomicReference<>(List.of());
        when(parsedObservationRepository.saveAllAndFlush(org.mockito.ArgumentMatchers.anyList()))
            .thenAnswer(invocation -> {
                storedObservations.set(invocation.getArgument(0));
                return invocation.getArgument(0);
            });
        when(parsedObservationRepository.findByReportIdOrderByCreatedAtAsc(reportId))
            .thenAnswer(invocation -> storedObservations.get());

        List<ParsedObservationResponse> responses = parsedObservationService.parse(reportId);

        ArgumentCaptor<List<ParsedObservation>> observationsCaptor = ArgumentCaptor.forClass(List.class);
        verify(parsedObservationRepository).deleteByReportIdAndStatus(reportId, ParsedObservationStatus.NEEDS_REVIEW);
        verify(parsedObservationRepository).saveAllAndFlush(observationsCaptor.capture());
        verify(parsedObservationRepository).findByReportIdOrderByCreatedAtAsc(reportId);

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
    void parseDeduplicatesCandidatesWithinSingleRun() {
        UUID userId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID reportId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID hemoglobinId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID glucoseId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        Report report = report(reportId, userId);
        report.markTextExtracted("""
            Hemoglobin 12.8 g/dL 12.0 - 15.5
            Hemoglobin 12.80 g/dL 12.0 - 15.5
            Glucose 91 mg/dL 70 - 99
            Glucose 91.0000 mg/dL 70 - 99
            """, Instant.parse("2026-07-10T13:00:00Z"));

        allowDefaultUserAccess(userId);
        when(reportRepository.findByIdForUpdate(reportId)).thenReturn(Optional.of(report));
        when(testCatalogLookupService.findAllForMatching()).thenReturn(List.of(
            new TestCatalogMatch(hemoglobinId, "hemoglobin", "Hemoglobin", "g/dL"),
            new TestCatalogMatch(glucoseId, "glucose", "Glucose", "mg/dL")
        ));
        when(parsedObservationRepository.findByReportIdAndStatusInOrderByCreatedAtAsc(reportId, List.of(ParsedObservationStatus.CONFIRMED, ParsedObservationStatus.REJECTED)))
            .thenReturn(List.of());
        AtomicReference<List<ParsedObservation>> storedObservations = new AtomicReference<>(List.of());
        when(parsedObservationRepository.saveAllAndFlush(org.mockito.ArgumentMatchers.anyList()))
            .thenAnswer(invocation -> {
                storedObservations.set(invocation.getArgument(0));
                return invocation.getArgument(0);
            });
        when(parsedObservationRepository.findByReportIdOrderByCreatedAtAsc(reportId))
            .thenAnswer(invocation -> storedObservations.get());

        List<ParsedObservationResponse> responses = parsedObservationService.parse(reportId);

        ArgumentCaptor<List<ParsedObservation>> observationsCaptor = ArgumentCaptor.forClass(List.class);
        verify(parsedObservationRepository).saveAllAndFlush(observationsCaptor.capture());

        assertThat(observationsCaptor.getValue()).hasSize(2);
        assertThat(observationsCaptor.getValue()).extracting(ParsedObservation::getRawTestName)
            .containsExactly("Hemoglobin", "Glucose");
        assertThat(responses).extracting(ParsedObservationResponse::rawTestName)
            .containsExactly("Hemoglobin", "Glucose");
    }

    @Test
    void parsingSameReportTwiceReplacesNeedsReviewRowsWithoutDuplicating() {
        UUID userId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID reportId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID hemoglobinId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID glucoseId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        Report report = report(reportId, userId);
        report.markTextExtracted("""
            Hemoglobin 12.8 g/dL 12.0 - 15.5
            Glucose 91 mg/dL 70 - 99
            """, Instant.parse("2026-07-10T13:00:00Z"));
        AtomicReference<List<ParsedObservation>> storedObservations = new AtomicReference<>(List.of());

        allowDefaultUserAccess(userId);
        when(reportRepository.findByIdForUpdate(reportId)).thenReturn(Optional.of(report));
        when(testCatalogLookupService.findAllForMatching()).thenReturn(List.of(
            new TestCatalogMatch(hemoglobinId, "hemoglobin", "Hemoglobin", "g/dL"),
            new TestCatalogMatch(glucoseId, "glucose", "Glucose", "mg/dL")
        ));
        when(parsedObservationRepository.findByReportIdAndStatusInOrderByCreatedAtAsc(reportId, List.of(ParsedObservationStatus.CONFIRMED, ParsedObservationStatus.REJECTED)))
            .thenReturn(List.of());
        doAnswer(invocation -> {
            storedObservations.set(List.of());
            return null;
        }).when(parsedObservationRepository).deleteByReportIdAndStatus(reportId, ParsedObservationStatus.NEEDS_REVIEW);
        when(parsedObservationRepository.saveAllAndFlush(org.mockito.ArgumentMatchers.anyList()))
            .thenAnswer(invocation -> {
                storedObservations.set(invocation.getArgument(0));
                return invocation.getArgument(0);
            });
        when(parsedObservationRepository.findByReportIdOrderByCreatedAtAsc(reportId))
            .thenAnswer(invocation -> storedObservations.get());

        List<ParsedObservationResponse> firstParse = parsedObservationService.parse(reportId);
        List<ParsedObservationResponse> secondParse = parsedObservationService.parse(reportId);

        ArgumentCaptor<List<ParsedObservation>> observationsCaptor = ArgumentCaptor.forClass(List.class);
        verify(parsedObservationRepository, org.mockito.Mockito.times(2)).saveAllAndFlush(observationsCaptor.capture());
        assertThat(firstParse).hasSize(2);
        assertThat(secondParse).hasSize(2);
        assertThat(observationsCaptor.getAllValues()).allSatisfy(savedObservations ->
            assertThat(savedObservations).hasSize(2)
        );
    }

    @Test
    void parseSkipsCandidatesAlreadyConfirmedByMatchedTest() {
        UUID userId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID reportId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID confirmedParsedObservationId = UUID.fromString("44444444-4444-4444-4444-444444444444");
        UUID hemoglobinId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        Report report = report(reportId, userId);
        report.markTextExtracted("Hemoglobin 12.8 g/dL 12.0 - 15.5", Instant.parse("2026-07-10T13:00:00Z"));
        ParsedObservation confirmedObservation = parsedObservation(
            confirmedParsedObservationId,
            reportId,
            hemoglobinId,
            LocalDate.parse("2026-07-09"),
            new BigDecimal("12.8000")
        );
        confirmedObservation.setUnit(" g/dL ");
        confirmedObservation.markConfirmed(UUID.fromString("66666666-6666-6666-6666-666666666666"), Instant.parse("2026-07-10T15:00:00Z"));

        allowDefaultUserAccess(userId);
        when(reportRepository.findByIdForUpdate(reportId)).thenReturn(Optional.of(report));
        when(testCatalogLookupService.findAllForMatching()).thenReturn(List.of(
            new TestCatalogMatch(hemoglobinId, "hemoglobin", "Hemoglobin", "g/dL")
        ));
        when(parsedObservationRepository.findByReportIdAndStatusInOrderByCreatedAtAsc(reportId, List.of(ParsedObservationStatus.CONFIRMED, ParsedObservationStatus.REJECTED)))
            .thenReturn(List.of(confirmedObservation));
        when(parsedObservationRepository.findByReportIdOrderByCreatedAtAsc(reportId))
            .thenReturn(List.of(confirmedObservation));

        List<ParsedObservationResponse> responses = parsedObservationService.parse(reportId);

        verify(parsedObservationRepository).deleteByReportIdAndStatus(reportId, ParsedObservationStatus.NEEDS_REVIEW);
        verify(parsedObservationRepository, never()).saveAllAndFlush(any());
        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().id()).isEqualTo(confirmedParsedObservationId);
        assertThat(responses.getFirst().status()).isEqualTo("CONFIRMED");
    }

    @Test
    void parseSkipsCandidateAlreadyConfirmedAfterValueWasEditedAndStillAddsNewCandidates() {
        UUID userId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID reportId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID confirmedParsedObservationId = UUID.fromString("44444444-4444-4444-4444-444444444444");
        UUID hemoglobinId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID wbcId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        UUID confirmedLabObservationId = UUID.fromString("66666666-6666-6666-6666-666666666666");
        Report report = report(reportId, userId);
        report.markTextExtracted("""
            Hemoglobin 12.8 g/dL 12.0 - 15.5
            WBC 6.4 10^3/uL 4.0 - 11.0
            """, Instant.parse("2026-07-10T13:00:00Z"));
        ParsedObservation confirmedObservation = parsedObservation(
            confirmedParsedObservationId,
            reportId,
            hemoglobinId,
            LocalDate.parse("2026-07-09"),
            new BigDecimal("13.1")
        );
        confirmedObservation.setRawValue("13.1");
        confirmedObservation.markConfirmed(confirmedLabObservationId, Instant.parse("2026-07-10T15:00:00Z"));

        allowDefaultUserAccess(userId);
        when(reportRepository.findByIdForUpdate(reportId)).thenReturn(Optional.of(report));
        when(testCatalogLookupService.findAllForMatching()).thenReturn(List.of(
            new TestCatalogMatch(hemoglobinId, "hemoglobin", "Hemoglobin", "g/dL"),
            new TestCatalogMatch(wbcId, "wbc", "WBC", "10^3/uL")
        ));
        when(parsedObservationRepository.findByReportIdAndStatusInOrderByCreatedAtAsc(reportId, List.of(ParsedObservationStatus.CONFIRMED, ParsedObservationStatus.REJECTED)))
            .thenReturn(List.of(confirmedObservation));
        AtomicReference<List<ParsedObservation>> storedObservations = new AtomicReference<>(List.of(confirmedObservation));
        when(parsedObservationRepository.saveAllAndFlush(org.mockito.ArgumentMatchers.anyList()))
            .thenAnswer(invocation -> {
                List<ParsedObservation> newObservations = invocation.getArgument(0);
                storedObservations.set(List.of(confirmedObservation, newObservations.getFirst()));
                return newObservations;
            });
        when(parsedObservationRepository.findByReportIdOrderByCreatedAtAsc(reportId))
            .thenAnswer(invocation -> storedObservations.get());

        List<ParsedObservationResponse> responses = parsedObservationService.parse(reportId);

        ArgumentCaptor<List<ParsedObservation>> observationsCaptor = ArgumentCaptor.forClass(List.class);
        verify(parsedObservationRepository).saveAllAndFlush(observationsCaptor.capture());
        assertThat(observationsCaptor.getValue()).hasSize(1);
        assertThat(observationsCaptor.getValue().getFirst().getRawTestName()).isEqualTo("WBC");
        assertThat(responses).extracting(ParsedObservationResponse::id)
            .contains(confirmedParsedObservationId);
        assertThat(responses).filteredOn(response -> response.status().equals("NEEDS_REVIEW"))
            .extracting(ParsedObservationResponse::rawTestName)
            .containsExactly("WBC");
        assertThat(confirmedObservation.getStatus()).isEqualTo(ParsedObservationStatus.CONFIRMED);
        assertThat(confirmedObservation.getConfirmedObservationId()).isEqualTo(confirmedLabObservationId);
        assertThat(confirmedObservation.getNumericValue()).isEqualByComparingTo("13.1");
    }

    @Test
    void parseSkipsCandidatesAlreadyConfirmedByRawTestNameWhenCandidateIsUnmatched() {
        UUID userId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID reportId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID confirmedParsedObservationId = UUID.fromString("44444444-4444-4444-4444-444444444444");
        Report report = report(reportId, userId);
        report.markTextExtracted("hemoglobin 12.8 g/dL 12.0 - 15.5", Instant.parse("2026-07-10T13:00:00Z"));
        ParsedObservation confirmedObservation = parsedObservation(
            confirmedParsedObservationId,
            reportId,
            null,
            LocalDate.parse("2026-07-09"),
            new BigDecimal("12.8")
        );
        confirmedObservation.markConfirmed(UUID.fromString("66666666-6666-6666-6666-666666666666"), Instant.parse("2026-07-10T15:00:00Z"));

        allowDefaultUserAccess(userId);
        when(reportRepository.findByIdForUpdate(reportId)).thenReturn(Optional.of(report));
        when(testCatalogLookupService.findAllForMatching()).thenReturn(List.of());
        when(parsedObservationRepository.findByReportIdAndStatusInOrderByCreatedAtAsc(reportId, List.of(ParsedObservationStatus.CONFIRMED, ParsedObservationStatus.REJECTED)))
            .thenReturn(List.of(confirmedObservation));
        when(parsedObservationRepository.findByReportIdOrderByCreatedAtAsc(reportId))
            .thenReturn(List.of(confirmedObservation));

        List<ParsedObservationResponse> responses = parsedObservationService.parse(reportId);

        verify(parsedObservationRepository, never()).saveAllAndFlush(any());
        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().id()).isEqualTo(confirmedParsedObservationId);
    }

    @Test
    void parsePreservesRejectedParsedObservationAndDoesNotReinsertEquivalentCandidate() {
        UUID userId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID reportId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID rejectedParsedObservationId = UUID.fromString("44444444-4444-4444-4444-444444444444");
        UUID hemoglobinId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        Report report = report(reportId, userId);
        report.markTextExtracted("Hemoglobin 12.8 g/dL 12.0 - 15.5", Instant.parse("2026-07-10T13:00:00Z"));
        ParsedObservation rejectedObservation = parsedObservation(
            rejectedParsedObservationId,
            reportId,
            hemoglobinId,
            LocalDate.parse("2026-07-09"),
            new BigDecimal("12.8000")
        );
        rejectedObservation.markRejected();

        allowDefaultUserAccess(userId);
        when(reportRepository.findByIdForUpdate(reportId)).thenReturn(Optional.of(report));
        when(testCatalogLookupService.findAllForMatching()).thenReturn(List.of(
            new TestCatalogMatch(hemoglobinId, "hemoglobin", "Hemoglobin", "g/dL")
        ));
        when(parsedObservationRepository.findByReportIdAndStatusInOrderByCreatedAtAsc(reportId, List.of(ParsedObservationStatus.CONFIRMED, ParsedObservationStatus.REJECTED)))
            .thenReturn(List.of(rejectedObservation));
        when(parsedObservationRepository.findByReportIdOrderByCreatedAtAsc(reportId))
            .thenReturn(List.of(rejectedObservation));

        List<ParsedObservationResponse> responses = parsedObservationService.parse(reportId);

        verify(parsedObservationRepository).deleteByReportIdAndStatus(reportId, ParsedObservationStatus.NEEDS_REVIEW);
        verify(parsedObservationRepository, never()).saveAllAndFlush(any());
        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().id()).isEqualTo(rejectedParsedObservationId);
        assertThat(responses.getFirst().status()).isEqualTo("REJECTED");
    }

    @Test
    void parsePreservesRejectedParsedObservationAfterValueWasEdited() {
        UUID userId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID reportId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID rejectedParsedObservationId = UUID.fromString("44444444-4444-4444-4444-444444444444");
        UUID hemoglobinId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        Report report = report(reportId, userId);
        report.markTextExtracted("Hemoglobin 12.8 g/dL 12.0 - 15.5", Instant.parse("2026-07-10T13:00:00Z"));
        ParsedObservation rejectedObservation = parsedObservation(
            rejectedParsedObservationId,
            reportId,
            hemoglobinId,
            LocalDate.parse("2026-07-09"),
            new BigDecimal("13.1")
        );
        rejectedObservation.setRawValue("13.1");
        rejectedObservation.markRejected();

        allowDefaultUserAccess(userId);
        when(reportRepository.findByIdForUpdate(reportId)).thenReturn(Optional.of(report));
        when(testCatalogLookupService.findAllForMatching()).thenReturn(List.of(
            new TestCatalogMatch(hemoglobinId, "hemoglobin", "Hemoglobin", "g/dL")
        ));
        when(parsedObservationRepository.findByReportIdAndStatusInOrderByCreatedAtAsc(reportId, List.of(ParsedObservationStatus.CONFIRMED, ParsedObservationStatus.REJECTED)))
            .thenReturn(List.of(rejectedObservation));
        when(parsedObservationRepository.findByReportIdOrderByCreatedAtAsc(reportId))
            .thenReturn(List.of(rejectedObservation));

        List<ParsedObservationResponse> responses = parsedObservationService.parse(reportId);

        verify(parsedObservationRepository).deleteByReportIdAndStatus(reportId, ParsedObservationStatus.NEEDS_REVIEW);
        verify(parsedObservationRepository, never()).saveAllAndFlush(any());
        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().id()).isEqualTo(rejectedParsedObservationId);
        assertThat(responses.getFirst().status()).isEqualTo("REJECTED");
        assertThat(rejectedObservation.getNumericValue()).isEqualByComparingTo("13.1");
    }

    @Test
    void parseDeletesOnlyUnconfirmedParsedRowsAndReturnsCurrentRows() {
        UUID userId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID reportId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID confirmedParsedObservationId = UUID.fromString("44444444-4444-4444-4444-444444444444");
        UUID confirmedLabObservationId = UUID.fromString("66666666-6666-6666-6666-666666666666");
        UUID hemoglobinId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        Report report = report(reportId, userId);
        report.markTextExtracted("Hemoglobin 12.8 g/dL 12.0 - 15.5", Instant.parse("2026-07-10T13:00:00Z"));
        ParsedObservation confirmedObservation = parsedObservation(
            confirmedParsedObservationId,
            reportId,
            hemoglobinId,
            LocalDate.parse("2026-07-08"),
            new BigDecimal("12.2")
        );
        confirmedObservation.markConfirmed(confirmedLabObservationId, Instant.parse("2026-07-10T15:00:00Z"));

        allowDefaultUserAccess(userId);
        when(reportRepository.findByIdForUpdate(reportId)).thenReturn(Optional.of(report));
        when(testCatalogLookupService.findAllForMatching()).thenReturn(List.of(
            new TestCatalogMatch(hemoglobinId, "hemoglobin", "Hemoglobin", "g/dL")
        ));
        when(parsedObservationRepository.findByReportIdAndStatusInOrderByCreatedAtAsc(reportId, List.of(ParsedObservationStatus.CONFIRMED, ParsedObservationStatus.REJECTED)))
            .thenReturn(List.of(confirmedObservation));
        AtomicReference<List<ParsedObservation>> storedObservations = new AtomicReference<>(List.of(confirmedObservation));
        when(parsedObservationRepository.saveAllAndFlush(org.mockito.ArgumentMatchers.anyList()))
            .thenAnswer(invocation -> {
                List<ParsedObservation> newObservations = invocation.getArgument(0);
                storedObservations.set(List.of(confirmedObservation, newObservations.getFirst()));
                return newObservations;
            });
        when(parsedObservationRepository.findByReportIdOrderByCreatedAtAsc(reportId))
            .thenAnswer(invocation -> storedObservations.get());

        List<ParsedObservationResponse> responses = parsedObservationService.parse(reportId);

        ArgumentCaptor<List<ParsedObservation>> observationsCaptor = ArgumentCaptor.forClass(List.class);
        verify(parsedObservationRepository).deleteByReportIdAndStatus(reportId, ParsedObservationStatus.NEEDS_REVIEW);
        verify(parsedObservationRepository).saveAllAndFlush(observationsCaptor.capture());

        assertThat(observationsCaptor.getValue()).hasSize(1);
        assertThat(observationsCaptor.getValue().getFirst().getStatus()).isEqualTo(ParsedObservationStatus.NEEDS_REVIEW);
        assertThat(responses).extracting(ParsedObservationResponse::id)
            .contains(confirmedParsedObservationId);
        assertThat(responses).extracting(ParsedObservationResponse::status)
            .containsExactly("CONFIRMED", "NEEDS_REVIEW");
    }

    @Test
    void parseRefreshesOnlyCandidatesNotAlreadyConfirmed() {
        UUID userId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID reportId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID confirmedParsedObservationId = UUID.fromString("44444444-4444-4444-4444-444444444444");
        UUID hemoglobinId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID wbcId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        Report report = report(reportId, userId);
        report.markTextExtracted("""
            Hemoglobin 12.8 g/dL 12.0 - 15.5
            WBC 6.4 10^3/uL 4.0 - 11.0
            """, Instant.parse("2026-07-10T13:00:00Z"));
        ParsedObservation confirmedObservation = parsedObservation(
            confirmedParsedObservationId,
            reportId,
            hemoglobinId,
            LocalDate.parse("2026-07-09"),
            new BigDecimal("12.8")
        );
        confirmedObservation.markConfirmed(UUID.fromString("66666666-6666-6666-6666-666666666666"), Instant.parse("2026-07-10T15:00:00Z"));

        allowDefaultUserAccess(userId);
        when(reportRepository.findByIdForUpdate(reportId)).thenReturn(Optional.of(report));
        when(testCatalogLookupService.findAllForMatching()).thenReturn(List.of(
            new TestCatalogMatch(hemoglobinId, "hemoglobin", "Hemoglobin", "g/dL"),
            new TestCatalogMatch(wbcId, "wbc", "WBC", "10^3/uL")
        ));
        when(parsedObservationRepository.findByReportIdAndStatusInOrderByCreatedAtAsc(reportId, List.of(ParsedObservationStatus.CONFIRMED, ParsedObservationStatus.REJECTED)))
            .thenReturn(List.of(confirmedObservation));
        AtomicReference<List<ParsedObservation>> storedObservations = new AtomicReference<>(List.of(confirmedObservation));
        when(parsedObservationRepository.saveAllAndFlush(org.mockito.ArgumentMatchers.anyList()))
            .thenAnswer(invocation -> {
                List<ParsedObservation> newObservations = invocation.getArgument(0);
                storedObservations.set(List.of(confirmedObservation, newObservations.getFirst()));
                return newObservations;
            });
        when(parsedObservationRepository.findByReportIdOrderByCreatedAtAsc(reportId))
            .thenAnswer(invocation -> storedObservations.get());

        List<ParsedObservationResponse> responses = parsedObservationService.parse(reportId);

        ArgumentCaptor<List<ParsedObservation>> observationsCaptor = ArgumentCaptor.forClass(List.class);
        verify(parsedObservationRepository).saveAllAndFlush(observationsCaptor.capture());
        assertThat(observationsCaptor.getValue()).hasSize(1);
        assertThat(observationsCaptor.getValue().getFirst().getRawTestName()).isEqualTo("WBC");
        assertThat(responses).extracting(ParsedObservationResponse::status)
            .containsExactly("CONFIRMED", "NEEDS_REVIEW");
    }

    @Test
    void parseRejectsReportWithoutExtractedText() {
        UUID userId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID reportId = UUID.fromString("33333333-3333-3333-3333-333333333333");

        allowDefaultUserAccess(userId);
        when(reportRepository.findByIdForUpdate(reportId)).thenReturn(Optional.of(report(reportId, userId)));

        assertThatThrownBy(() -> parsedObservationService.parse(reportId))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void parseRejectsUnknownReport() {
        UUID userId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID reportId = UUID.fromString("33333333-3333-3333-3333-333333333333");

        allowDefaultUserAccess(userId);
        when(reportRepository.findByIdForUpdate(reportId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> parsedObservationService.parse(reportId))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void findByReportIdRequiresReportForDefaultUser() {
        UUID userId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID reportId = UUID.fromString("33333333-3333-3333-3333-333333333333");

        allowDefaultUserAccess(userId);
        when(reportRepository.findById(reportId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> parsedObservationService.findByReportId(reportId))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void findByReportIdRemovesDuplicateNeedsReviewRowsBeforeReturningRows() {
        UUID userId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID reportId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID firstParsedObservationId = UUID.fromString("55555555-5555-5555-5555-555555555555");
        UUID duplicateParsedObservationId = UUID.fromString("77777777-7777-7777-7777-777777777777");
        UUID confirmedParsedObservationId = UUID.fromString("88888888-8888-8888-8888-888888888888");
        UUID testId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        ParsedObservation firstObservation = parsedObservation(
            firstParsedObservationId,
            reportId,
            testId,
            LocalDate.parse("2026-07-09"),
            new BigDecimal("12.8000")
        );
        ParsedObservation duplicateObservation = parsedObservation(
            duplicateParsedObservationId,
            reportId,
            testId,
            LocalDate.parse("2026-07-09"),
            new BigDecimal("12.80")
        );
        ParsedObservation confirmedObservation = parsedObservation(
            confirmedParsedObservationId,
            reportId,
            testId,
            LocalDate.parse("2026-07-10"),
            new BigDecimal("13.1")
        );
        confirmedObservation.markConfirmed(UUID.fromString("66666666-6666-6666-6666-666666666666"), Instant.parse("2026-07-10T15:00:00Z"));
        AtomicReference<List<ParsedObservation>> storedObservations = new AtomicReference<>(
            List.of(firstObservation, duplicateObservation, confirmedObservation)
        );

        allowDefaultUserAccess(userId);
        when(reportRepository.findById(reportId)).thenReturn(Optional.of(report(reportId, userId)));
        when(parsedObservationRepository.findByReportIdAndStatusOrderByCreatedAtAsc(reportId, ParsedObservationStatus.NEEDS_REVIEW))
            .thenAnswer(invocation -> storedObservations.get().stream()
                .filter(observation -> observation.getStatus() == ParsedObservationStatus.NEEDS_REVIEW)
                .toList());
        doAnswer(invocation -> {
            List<ParsedObservation> duplicates = invocation.getArgument(0);
            storedObservations.set(storedObservations.get().stream()
                .filter(observation -> !duplicates.contains(observation))
                .toList());
            return null;
        }).when(parsedObservationRepository).deleteAllInBatch(org.mockito.ArgumentMatchers.anyList());
        when(parsedObservationRepository.findByReportIdOrderByCreatedAtAsc(reportId))
            .thenAnswer(invocation -> storedObservations.get());

        List<ParsedObservationResponse> responses = parsedObservationService.findByReportId(reportId);

        ArgumentCaptor<List<ParsedObservation>> duplicateCaptor = ArgumentCaptor.forClass(List.class);
        verify(parsedObservationRepository).deleteAllInBatch(duplicateCaptor.capture());
        assertThat(duplicateCaptor.getValue()).extracting(ParsedObservation::getId)
            .containsExactly(duplicateParsedObservationId);
        assertThat(responses).extracting(ParsedObservationResponse::id)
            .containsExactly(firstParsedObservationId, confirmedParsedObservationId);
    }

    @Test
    void findReviewQueueDoesNotReturnDuplicateNeedsReviewRows() {
        UUID patientUserId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID reportId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID firstParsedObservationId = UUID.fromString("55555555-5555-5555-5555-555555555555");
        UUID duplicateParsedObservationId = UUID.fromString("77777777-7777-7777-7777-777777777777");
        UUID testId = UUID.fromString("11111111-1111-1111-1111-111111111111");

        when(defaultUserProvider.resolveReadablePatientId(null)).thenReturn(patientUserId);
        when(parsedObservationRepository.findReviewQueueByPatientUserIdAndStatus(patientUserId, ParsedObservationStatus.NEEDS_REVIEW))
            .thenReturn(List.of(
                reviewProjection(firstParsedObservationId, reportId, testId, "Hemoglobin", "12.8000"),
                reviewProjection(duplicateParsedObservationId, reportId, testId, "Hemoglobin", "12.80")
            ));

        List<ParsedObservationReviewResponse> responses =
            parsedObservationService.findReviewQueue(null, ParsedObservationStatus.NEEDS_REVIEW);

        assertThat(responses).extracting(ParsedObservationReviewResponse::parsedObservationId)
            .containsExactly(firstParsedObservationId);
    }

    @Test
    void updateChangesEditableFieldsAndKeepsNeedsReviewStatus() {
        UUID userId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID reportId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID parsedObservationId = UUID.fromString("55555555-5555-5555-5555-555555555555");
        UUID originalTestId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID matchedTestId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        ParsedObservation parsedObservation = parsedObservation(
            parsedObservationId,
            reportId,
            originalTestId,
            LocalDate.parse("2026-07-08"),
            new BigDecimal("12.8")
        );
        UpdateParsedObservationRequest request = new UpdateParsedObservationRequest();
        request.setRawTestName("White Blood Cell Count");
        request.setMatchedTestId(matchedTestId);
        request.setObservedAt(LocalDate.parse("2026-07-10"));
        request.setRawValue("6.4");
        request.setNumericValue(new BigDecimal("6.4"));
        request.setUnit("10^3/uL");
        request.setReferenceRange("4.0 - 11.0");

        when(parsedObservationRepository.findById(parsedObservationId)).thenReturn(Optional.of(parsedObservation));
        allowDefaultUserAccess(userId);
        when(reportRepository.findByIdForUpdate(reportId)).thenReturn(Optional.of(report(reportId, userId)));
        when(testCatalogLookupService.findById(matchedTestId))
            .thenReturn(Optional.of(new TestCatalogLookup(matchedTestId, "WBC", "10^3/uL")));

        ParsedObservationResponse response = parsedObservationService.update(parsedObservationId, request);

        assertThat(response.rawTestName()).isEqualTo("White Blood Cell Count");
        assertThat(response.matchedTestId()).isEqualTo(matchedTestId);
        assertThat(response.observedAt()).isEqualTo(LocalDate.parse("2026-07-10"));
        assertThat(response.rawValue()).isEqualTo("6.4");
        assertThat(response.numericValue()).isEqualByComparingTo("6.4");
        assertThat(response.unit()).isEqualTo("10^3/uL");
        assertThat(response.referenceRange()).isEqualTo("4.0 - 11.0");
        assertThat(response.status()).isEqualTo("NEEDS_REVIEW");
        assertThat(parsedObservation.getStatus()).isEqualTo(ParsedObservationStatus.NEEDS_REVIEW);
    }

    @Test
    void updateAllowsClearingNullableFields() {
        UUID userId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID reportId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID parsedObservationId = UUID.fromString("55555555-5555-5555-5555-555555555555");
        UUID testId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        ParsedObservation parsedObservation = parsedObservation(
            parsedObservationId,
            reportId,
            testId,
            LocalDate.parse("2026-07-08"),
            new BigDecimal("12.8")
        );
        UpdateParsedObservationRequest request = new UpdateParsedObservationRequest();
        request.setMatchedTestId(null);
        request.setObservedAt(null);
        request.setRawValue(null);
        request.setNumericValue(null);
        request.setUnit(null);
        request.setReferenceRange(null);

        when(parsedObservationRepository.findById(parsedObservationId)).thenReturn(Optional.of(parsedObservation));
        allowDefaultUserAccess(userId);
        when(reportRepository.findByIdForUpdate(reportId)).thenReturn(Optional.of(report(reportId, userId)));

        ParsedObservationResponse response = parsedObservationService.update(parsedObservationId, request);

        assertThat(response.rawTestName()).isEqualTo("Hemoglobin");
        assertThat(response.matchedTestId()).isNull();
        assertThat(response.observedAt()).isNull();
        assertThat(response.rawValue()).isNull();
        assertThat(response.numericValue()).isNull();
        assertThat(response.unit()).isNull();
        assertThat(response.referenceRange()).isNull();
    }

    @Test
    void updateRejectsUnknownParsedObservation() {
        UUID parsedObservationId = UUID.fromString("55555555-5555-5555-5555-555555555555");
        UpdateParsedObservationRequest request = new UpdateParsedObservationRequest();
        request.setRawValue("6.4");

        when(parsedObservationRepository.findById(parsedObservationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> parsedObservationService.update(parsedObservationId, request))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void updateRejectsConfirmedParsedObservation() {
        UUID userId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID reportId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID parsedObservationId = UUID.fromString("55555555-5555-5555-5555-555555555555");
        UUID testId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        ParsedObservation parsedObservation = parsedObservation(
            parsedObservationId,
            reportId,
            testId,
            LocalDate.parse("2026-07-08"),
            new BigDecimal("12.8")
        );
        parsedObservation.markConfirmed();
        UpdateParsedObservationRequest request = new UpdateParsedObservationRequest();
        request.setRawValue("13.2");

        when(parsedObservationRepository.findById(parsedObservationId)).thenReturn(Optional.of(parsedObservation));
        allowDefaultUserAccess(userId);
        when(reportRepository.findByIdForUpdate(reportId)).thenReturn(Optional.of(report(reportId, userId)));

        assertThatThrownBy(() -> parsedObservationService.update(parsedObservationId, request))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void updateRejectsUnknownMatchedTest() {
        UUID userId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID reportId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID parsedObservationId = UUID.fromString("55555555-5555-5555-5555-555555555555");
        UUID matchedTestId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        ParsedObservation parsedObservation = parsedObservation(
            parsedObservationId,
            reportId,
            null,
            LocalDate.parse("2026-07-08"),
            new BigDecimal("12.8")
        );
        UpdateParsedObservationRequest request = new UpdateParsedObservationRequest();
        request.setMatchedTestId(matchedTestId);

        when(parsedObservationRepository.findById(parsedObservationId)).thenReturn(Optional.of(parsedObservation));
        allowDefaultUserAccess(userId);
        when(reportRepository.findByIdForUpdate(reportId)).thenReturn(Optional.of(report(reportId, userId)));
        when(testCatalogLookupService.findById(matchedTestId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> parsedObservationService.update(parsedObservationId, request))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void confirmUsesEditedParsedObservationValues() {
        UUID userId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID reportId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID parsedObservationId = UUID.fromString("55555555-5555-5555-5555-555555555555");
        UUID originalTestId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID matchedTestId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        ParsedObservation parsedObservation = parsedObservation(
            parsedObservationId,
            reportId,
            originalTestId,
            LocalDate.parse("2026-07-08"),
            new BigDecimal("12.8")
        );
        UpdateParsedObservationRequest request = new UpdateParsedObservationRequest();
        request.setMatchedTestId(matchedTestId);
        request.setObservedAt(LocalDate.parse("2026-07-10"));
        request.setRawValue("6.4");
        request.setNumericValue(new BigDecimal("6.4"));
        request.setUnit("10^3/uL");
        request.setReferenceRange("4.0 - 11.0");

        when(parsedObservationRepository.findById(parsedObservationId)).thenReturn(Optional.of(parsedObservation));
        when(parsedObservationRepository.findByIdForUpdate(parsedObservationId)).thenReturn(Optional.of(parsedObservation));
        allowDefaultUserAccess(userId);
        when(reportRepository.findByIdForUpdate(reportId)).thenReturn(Optional.of(report(reportId, userId)));
        when(testCatalogLookupService.findById(matchedTestId))
            .thenReturn(Optional.of(new TestCatalogLookup(matchedTestId, "WBC", "10^3/uL")));
        when(labObservationService.create(any(CreateLabObservationCommand.class))).thenReturn(new LabObservationResponse(
            UUID.randomUUID(),
            matchedTestId,
            "WBC",
            LocalDate.parse("2026-07-10"),
            new BigDecimal("6.4"),
            "10^3/uL",
            new BigDecimal("4.0"),
            new BigDecimal("11.0"),
            "normal"
        ));

        parsedObservationService.update(parsedObservationId, request);
        parsedObservationService.confirm(parsedObservationId);

        ArgumentCaptor<CreateLabObservationCommand> commandCaptor = ArgumentCaptor.forClass(CreateLabObservationCommand.class);
        verify(labObservationService).create(commandCaptor.capture());
        CreateLabObservationCommand command = commandCaptor.getValue();
        assertThat(command.testId()).isEqualTo(matchedTestId);
        assertThat(command.observedAt()).isEqualTo(LocalDate.parse("2026-07-10"));
        assertThat(command.numericValue()).isEqualByComparingTo("6.4");
        assertThat(command.unit()).isEqualTo("10^3/uL");
        assertThat(command.referenceLow()).isEqualByComparingTo("4.0");
        assertThat(command.referenceHigh()).isEqualByComparingTo("11.0");
        assertThat(command.abnormalFlag()).isEqualTo("normal");
        assertThat(command.sourceReportId()).isEqualTo(reportId);
        assertThat(command.sourceParsedObservationId()).isEqualTo(parsedObservationId);
    }

    @Test
    void confirmCreatesLabObservationAndMarksParsedObservationConfirmed() {
        UUID userId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID reportId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID parsedObservationId = UUID.fromString("55555555-5555-5555-5555-555555555555");
        UUID testId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID labObservationId = UUID.fromString("66666666-6666-6666-6666-666666666666");
        ParsedObservation parsedObservation = parsedObservation(
            parsedObservationId,
            reportId,
            testId,
            LocalDate.parse("2026-07-08"),
            new BigDecimal("12.8")
        );

        when(parsedObservationRepository.findByIdForUpdate(parsedObservationId)).thenReturn(Optional.of(parsedObservation));
        allowDefaultUserAccess(userId);
        when(reportRepository.findByIdForUpdate(reportId)).thenReturn(Optional.of(report(reportId, userId)));
        when(labObservationService.create(any(CreateLabObservationCommand.class))).thenReturn(new LabObservationResponse(
            labObservationId,
            testId,
            "Hemoglobin",
            LocalDate.parse("2026-07-08"),
            new BigDecimal("12.8"),
            "g/dL",
            new BigDecimal("12.0"),
            new BigDecimal("15.5"),
            "normal"
        ));

        LabObservationResponse response = parsedObservationService.confirm(parsedObservationId);

        ArgumentCaptor<CreateLabObservationCommand> commandCaptor = ArgumentCaptor.forClass(CreateLabObservationCommand.class);
        verify(labObservationService).create(commandCaptor.capture());

        CreateLabObservationCommand command = commandCaptor.getValue();
        assertThat(command.testId()).isEqualTo(testId);
        assertThat(command.observedAt()).isEqualTo(LocalDate.parse("2026-07-08"));
        assertThat(command.numericValue()).isEqualByComparingTo("12.8");
        assertThat(command.unit()).isEqualTo("g/dL");
        assertThat(command.referenceLow()).isEqualByComparingTo("12.0");
        assertThat(command.referenceHigh()).isEqualByComparingTo("15.5");
        assertThat(command.abnormalFlag()).isEqualTo("normal");
        assertThat(command.sourceReportId()).isEqualTo(reportId);
        assertThat(command.sourceParsedObservationId()).isEqualTo(parsedObservationId);
        assertThat(parsedObservation.getStatus()).isEqualTo(ParsedObservationStatus.CONFIRMED);
        assertThat(parsedObservation.getConfirmedObservationId()).isEqualTo(labObservationId);
        assertThat(parsedObservation.getConfirmedAt()).isNotNull();
        assertThat(response.id()).isEqualTo(labObservationId);

        ArgumentCaptor<String> detailsCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditService).record(
            org.mockito.ArgumentMatchers.eq("PARSED_OBSERVATION_CONFIRMED"),
            org.mockito.ArgumentMatchers.eq(userId),
            org.mockito.ArgumentMatchers.eq("PARSED_OBSERVATION"),
            org.mockito.ArgumentMatchers.eq(parsedObservationId),
            detailsCaptor.capture()
        );
        assertThat(detailsCaptor.getValue()).contains(reportId.toString(), labObservationId.toString());
        assertThat(detailsCaptor.getValue()).doesNotContain("12.8", "Hemoglobin", "g/dL", "12.0 - 15.5");
    }

    @Test
    void confirmReturnsExistingLabObservationForAlreadyConfirmedParsedObservation() {
        UUID userId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID reportId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID parsedObservationId = UUID.fromString("55555555-5555-5555-5555-555555555555");
        UUID testId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID labObservationId = UUID.fromString("66666666-6666-6666-6666-666666666666");
        ParsedObservation parsedObservation = parsedObservation(
            parsedObservationId,
            reportId,
            testId,
            LocalDate.parse("2026-07-08"),
            new BigDecimal("12.8")
        );
        parsedObservation.markConfirmed(labObservationId, Instant.parse("2026-07-10T15:00:00Z"));
        LabObservationResponse existingResponse = new LabObservationResponse(
            labObservationId,
            testId,
            "Hemoglobin",
            LocalDate.parse("2026-07-08"),
            new BigDecimal("12.8"),
            "g/dL",
            new BigDecimal("12.0"),
            new BigDecimal("15.5"),
            "normal"
        );

        when(parsedObservationRepository.findByIdForUpdate(parsedObservationId)).thenReturn(Optional.of(parsedObservation));
        allowDefaultUserAccess(userId);
        when(reportRepository.findByIdForUpdate(reportId)).thenReturn(Optional.of(report(reportId, userId)));
        when(labObservationService.findByIdForDefaultUser(labObservationId)).thenReturn(Optional.of(existingResponse));

        LabObservationResponse response = parsedObservationService.confirm(parsedObservationId);

        assertThat(response).isEqualTo(existingResponse);
        verify(labObservationService, never()).create(any(CreateLabObservationCommand.class));
    }

    @Test
    void confirmReturnsExistingLabObservationForSameSourceParsedObservationAndMarksConfirmed() {
        UUID userId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID reportId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID parsedObservationId = UUID.fromString("55555555-5555-5555-5555-555555555555");
        UUID testId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID labObservationId = UUID.fromString("66666666-6666-6666-6666-666666666666");
        ParsedObservation parsedObservation = parsedObservation(
            parsedObservationId,
            reportId,
            testId,
            LocalDate.parse("2026-07-08"),
            new BigDecimal("12.8")
        );
        LabObservationResponse existingResponse = new LabObservationResponse(
            labObservationId,
            testId,
            "Hemoglobin",
            LocalDate.parse("2026-07-08"),
            new BigDecimal("12.8"),
            "g/dL",
            new BigDecimal("12.0"),
            new BigDecimal("15.5"),
            "normal"
        );

        when(parsedObservationRepository.findByIdForUpdate(parsedObservationId)).thenReturn(Optional.of(parsedObservation));
        allowDefaultUserAccess(userId);
        when(reportRepository.findByIdForUpdate(reportId)).thenReturn(Optional.of(report(reportId, userId)));
        when(labObservationService.findBySourceParsedObservationIdForDefaultUser(parsedObservationId))
            .thenReturn(Optional.of(existingResponse));

        LabObservationResponse response = parsedObservationService.confirm(parsedObservationId);

        assertThat(response).isEqualTo(existingResponse);
        assertThat(parsedObservation.getStatus()).isEqualTo(ParsedObservationStatus.CONFIRMED);
        assertThat(parsedObservation.getConfirmedObservationId()).isEqualTo(labObservationId);
        assertThat(parsedObservation.getConfirmedAt()).isNotNull();
        verify(labObservationService, never()).create(any(CreateLabObservationCommand.class));
    }

    @Test
    void confirmReturnsConflictForConfirmedParsedObservationWithoutLinkedLabObservation() {
        UUID userId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID reportId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID parsedObservationId = UUID.fromString("55555555-5555-5555-5555-555555555555");
        UUID testId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        ParsedObservation parsedObservation = parsedObservation(
            parsedObservationId,
            reportId,
            testId,
            LocalDate.parse("2026-07-08"),
            new BigDecimal("12.8")
        );
        parsedObservation.markConfirmed();

        when(parsedObservationRepository.findByIdForUpdate(parsedObservationId)).thenReturn(Optional.of(parsedObservation));
        allowDefaultUserAccess(userId);
        when(reportRepository.findByIdForUpdate(reportId)).thenReturn(Optional.of(report(reportId, userId)));

        assertThatThrownBy(() -> parsedObservationService.confirm(parsedObservationId))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
            .isEqualTo(HttpStatus.CONFLICT);
        verify(labObservationService, never()).create(any(CreateLabObservationCommand.class));
    }

    @Test
    void confirmUsesReportDateWhenParsedObservationDateIsMissing() {
        UUID userId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID reportId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID parsedObservationId = UUID.fromString("55555555-5555-5555-5555-555555555555");
        UUID testId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        ParsedObservation parsedObservation = parsedObservation(
            parsedObservationId,
            reportId,
            testId,
            null,
            new BigDecimal("12.8")
        );

        when(parsedObservationRepository.findByIdForUpdate(parsedObservationId)).thenReturn(Optional.of(parsedObservation));
        allowDefaultUserAccess(userId);
        when(reportRepository.findByIdForUpdate(reportId)).thenReturn(Optional.of(report(reportId, userId)));
        when(labObservationService.create(any(CreateLabObservationCommand.class))).thenReturn(new LabObservationResponse(
            UUID.randomUUID(),
            testId,
            "Hemoglobin",
            LocalDate.parse("2026-07-09"),
            new BigDecimal("12.8"),
            "g/dL",
            new BigDecimal("12.0"),
            new BigDecimal("15.5"),
            "normal"
        ));

        parsedObservationService.confirm(parsedObservationId);

        ArgumentCaptor<CreateLabObservationCommand> commandCaptor = ArgumentCaptor.forClass(CreateLabObservationCommand.class);
        verify(labObservationService).create(commandCaptor.capture());
        assertThat(commandCaptor.getValue().observedAt()).isEqualTo(LocalDate.parse("2026-07-09"));
    }

    @Test
    void confirmRejectsParsedObservationWithoutMatchedTest() {
        UUID userId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID reportId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID parsedObservationId = UUID.fromString("55555555-5555-5555-5555-555555555555");

        when(parsedObservationRepository.findByIdForUpdate(parsedObservationId)).thenReturn(Optional.of(parsedObservation(
            parsedObservationId,
            reportId,
            null,
            LocalDate.parse("2026-07-08"),
            new BigDecimal("12.8")
        )));
        allowDefaultUserAccess(userId);
        when(reportRepository.findByIdForUpdate(reportId)).thenReturn(Optional.of(report(reportId, userId)));

        assertThatThrownBy(() -> parsedObservationService.confirm(parsedObservationId))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void confirmRejectsParsedObservationWithoutNumericValue() {
        UUID userId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID reportId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID parsedObservationId = UUID.fromString("55555555-5555-5555-5555-555555555555");
        UUID testId = UUID.fromString("11111111-1111-1111-1111-111111111111");

        when(parsedObservationRepository.findByIdForUpdate(parsedObservationId)).thenReturn(Optional.of(parsedObservation(
            parsedObservationId,
            reportId,
            testId,
            LocalDate.parse("2026-07-08"),
            null
        )));
        allowDefaultUserAccess(userId);
        when(reportRepository.findByIdForUpdate(reportId)).thenReturn(Optional.of(report(reportId, userId)));

        assertThatThrownBy(() -> parsedObservationService.confirm(parsedObservationId))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void confirmReturnsNotFoundForUnknownParsedObservation() {
        UUID parsedObservationId = UUID.fromString("55555555-5555-5555-5555-555555555555");

        when(parsedObservationRepository.findByIdForUpdate(parsedObservationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> parsedObservationService.confirm(parsedObservationId))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void rejectMarksNeedsReviewParsedObservationRejectedAndAuditsMetadataOnly() {
        UUID userId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID reportId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID parsedObservationId = UUID.fromString("55555555-5555-5555-5555-555555555555");
        UUID testId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        ParsedObservation parsedObservation = parsedObservation(
            parsedObservationId,
            reportId,
            testId,
            LocalDate.parse("2026-07-08"),
            new BigDecimal("12.8")
        );

        when(parsedObservationRepository.findByIdForUpdate(parsedObservationId)).thenReturn(Optional.of(parsedObservation));
        allowDefaultUserAccess(userId);
        when(reportRepository.findByIdForUpdate(reportId)).thenReturn(Optional.of(report(reportId, userId)));
        when(testCatalogLookupService.findById(testId))
            .thenReturn(Optional.of(new TestCatalogLookup(testId, "Hemoglobin", "g/dL")));

        ParsedObservationReviewResponse response = parsedObservationService.reject(parsedObservationId);

        assertThat(parsedObservation.getStatus()).isEqualTo(ParsedObservationStatus.REJECTED);
        assertThat(response.parsedObservationId()).isEqualTo(parsedObservationId);
        assertThat(response.status()).isEqualTo("REJECTED");
        assertThat(response.testName()).isEqualTo("Hemoglobin");

        ArgumentCaptor<String> detailsCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditService).record(
            org.mockito.ArgumentMatchers.eq("PARSED_OBSERVATION_REJECTED"),
            org.mockito.ArgumentMatchers.eq(userId),
            org.mockito.ArgumentMatchers.eq("PARSED_OBSERVATION"),
            org.mockito.ArgumentMatchers.eq(parsedObservationId),
            detailsCaptor.capture()
        );
        assertThat(detailsCaptor.getValue()).contains(reportId.toString());
        assertThat(detailsCaptor.getValue()).doesNotContain("12.8", "Hemoglobin", "g/dL", "12.0 - 15.5");
    }

    @Test
    void rejectReturnsExistingRejectedParsedObservationWithoutDuplicateAudit() {
        UUID userId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID reportId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID parsedObservationId = UUID.fromString("55555555-5555-5555-5555-555555555555");
        ParsedObservation parsedObservation = parsedObservation(
            parsedObservationId,
            reportId,
            null,
            LocalDate.parse("2026-07-08"),
            new BigDecimal("12.8")
        );
        parsedObservation.markRejected();

        when(parsedObservationRepository.findByIdForUpdate(parsedObservationId)).thenReturn(Optional.of(parsedObservation));
        allowDefaultUserAccess(userId);
        when(reportRepository.findByIdForUpdate(reportId)).thenReturn(Optional.of(report(reportId, userId)));

        ParsedObservationReviewResponse response = parsedObservationService.reject(parsedObservationId);

        assertThat(response.status()).isEqualTo("REJECTED");
        verify(auditService, never()).record(any(), any(), any(), any(), any());
    }

    @Test
    void rejectConfirmedParsedObservationReturnsConflict() {
        UUID userId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID reportId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID parsedObservationId = UUID.fromString("55555555-5555-5555-5555-555555555555");
        ParsedObservation parsedObservation = parsedObservation(
            parsedObservationId,
            reportId,
            null,
            LocalDate.parse("2026-07-08"),
            new BigDecimal("12.8")
        );
        parsedObservation.markConfirmed();

        when(parsedObservationRepository.findByIdForUpdate(parsedObservationId)).thenReturn(Optional.of(parsedObservation));
        allowDefaultUserAccess(userId);
        when(reportRepository.findByIdForUpdate(reportId)).thenReturn(Optional.of(report(reportId, userId)));

        assertThatThrownBy(() -> parsedObservationService.reject(parsedObservationId))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
            .isEqualTo(HttpStatus.CONFLICT);
        verify(auditService, never()).record(any(), any(), any(), any(), any());
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

    private static ParsedObservation parsedObservation(
        UUID parsedObservationId,
        UUID reportId,
        UUID matchedTestId,
        LocalDate observedAt,
        BigDecimal numericValue
    ) {
        return new ParsedObservation(
            parsedObservationId,
            reportId,
            "Hemoglobin",
            matchedTestId,
            observedAt,
            numericValue == null ? null : numericValue.toPlainString(),
            numericValue,
            "g/dL",
            "12.0 - 15.5",
            ParsedObservationStatus.NEEDS_REVIEW
        );
    }

    private static ParsedObservationReviewProjection reviewProjection(
        UUID parsedObservationId,
        UUID reportId,
        UUID testId,
        String rawTestName,
        String valueText
    ) {
        return new TestParsedObservationReviewProjection(
            parsedObservationId,
            reportId,
            "lab-report-july.pdf",
            "Quest Diagnostics",
            LocalDate.parse("2026-07-09"),
            testId,
            rawTestName,
            rawTestName,
            LocalDate.parse("2026-07-09"),
            valueText,
            new BigDecimal(valueText),
            "g/dL",
            "12.0 - 15.5",
            ParsedObservationStatus.NEEDS_REVIEW,
            Instant.parse("2026-07-10T14:00:00Z")
        );
    }

    private record TestParsedObservationReviewProjection(
        UUID parsedObservationId,
        UUID reportId,
        String reportOriginalFilename,
        String labName,
        LocalDate reportDate,
        UUID testId,
        String testName,
        String rawTestName,
        LocalDate observedAt,
        String valueText,
        BigDecimal numericValue,
        String unit,
        String referenceRange,
        ParsedObservationStatus status,
        Instant createdAt
    ) implements ParsedObservationReviewProjection {

        @Override
        public UUID getParsedObservationId() {
            return parsedObservationId;
        }

        @Override
        public UUID getReportId() {
            return reportId;
        }

        @Override
        public String getReportOriginalFilename() {
            return reportOriginalFilename;
        }

        @Override
        public String getLabName() {
            return labName;
        }

        @Override
        public LocalDate getReportDate() {
            return reportDate;
        }

        @Override
        public UUID getTestId() {
            return testId;
        }

        @Override
        public String getTestName() {
            return testName;
        }

        @Override
        public String getRawTestName() {
            return rawTestName;
        }

        @Override
        public LocalDate getObservedAt() {
            return observedAt;
        }

        @Override
        public String getValueText() {
            return valueText;
        }

        @Override
        public BigDecimal getNumericValue() {
            return numericValue;
        }

        @Override
        public String getUnit() {
            return unit;
        }

        @Override
        public String getReferenceRange() {
            return referenceRange;
        }

        @Override
        public ParsedObservationStatus getStatus() {
            return status;
        }

        @Override
        public Instant getCreatedAt() {
            return createdAt;
        }
    }

    private void allowDefaultUserAccess(UUID userId) {
        lenient().when(defaultUserProvider.getCurrentUserId()).thenReturn(userId);
        lenient().when(defaultUserProvider.getDefaultUserId()).thenReturn(userId);
        lenient().when(defaultUserProvider.resolveReadablePatientId(null)).thenReturn(userId);
        lenient().when(defaultUserProvider.requireCurrentUserCanWritePatientData()).thenReturn(userId);
    }
}
