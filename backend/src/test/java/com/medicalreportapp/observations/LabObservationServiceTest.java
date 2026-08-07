package com.medicalreportapp.observations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.medicalreportapp.testcatalog.TestCatalogLookup;
import com.medicalreportapp.testcatalog.TestCatalogLookupService;
import java.math.BigDecimal;
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

@ExtendWith(MockitoExtension.class)
class LabObservationServiceTest {

    @Mock
    private LabObservationRepository labObservationRepository;

    @Mock
    private TestCatalogLookupService testCatalogLookupService;

    @Mock
    private DefaultUserProvider defaultUserProvider;

    @InjectMocks
    private LabObservationService labObservationService;

    @Test
    void createSavesObservationForDefaultUserAndReturnsTestName() {
        UUID testId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID userId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        CreateLabObservationRequest request = new CreateLabObservationRequest(
            testId,
            LocalDate.parse("2026-07-01"),
            new BigDecimal("95.5"),
            "mg/dL",
            new BigDecimal("70"),
            new BigDecimal("99"),
            "normal"
        );

        when(testCatalogLookupService.findById(testId)).thenReturn(Optional.of(new TestCatalogLookup(testId, "Glucose", "mg/dL")));
        when(defaultUserProvider.getDefaultUserId()).thenReturn(userId);
        when(labObservationRepository.findFirstByUserIdAndTestIdAndObservedAtAndNumericValueAndUnitAndSourceReportIdIsNullAndSourceParsedObservationIdIsNullOrderByIdAsc(
            userId,
            testId,
            request.observedAt(),
            request.numericValue(),
            request.unit()
        )).thenReturn(Optional.empty());
        when(labObservationRepository.save(any(LabObservation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LabObservationResponse response = labObservationService.create(request);

        ArgumentCaptor<LabObservation> observationCaptor = ArgumentCaptor.forClass(LabObservation.class);
        verify(labObservationRepository).save(observationCaptor.capture());

        LabObservation savedObservation = observationCaptor.getValue();
        assertThat(savedObservation.getId()).isNotNull();
        assertThat(savedObservation.getUserId()).isEqualTo(userId);
        assertThat(savedObservation.getTestId()).isEqualTo(testId);
        assertThat(savedObservation.getObservedAt()).isEqualTo(request.observedAt());
        assertThat(savedObservation.getNumericValue()).isEqualByComparingTo("95.5");
        assertThat(savedObservation.getUnit()).isEqualTo("mg/dL");
        assertThat(savedObservation.getReferenceLow()).isEqualByComparingTo("70");
        assertThat(savedObservation.getReferenceHigh()).isEqualByComparingTo("99");
        assertThat(savedObservation.getAbnormalFlag()).isEqualTo("normal");
        assertThat(savedObservation.getSourceReportId()).isNull();
        assertThat(savedObservation.getSourceParsedObservationId()).isNull();

        assertThat(response.id()).isEqualTo(savedObservation.getId());
        assertThat(response.testId()).isEqualTo(testId);
        assertThat(response.testName()).isEqualTo("Glucose");
        assertThat(response.observedAt()).isEqualTo(request.observedAt());
        assertThat(response.numericValue()).isEqualByComparingTo("95.5");
        assertThat(response.unit()).isEqualTo("mg/dL");
        assertThat(response.referenceLow()).isEqualByComparingTo("70");
        assertThat(response.referenceHigh()).isEqualByComparingTo("99");
        assertThat(response.abnormalFlag()).isEqualTo("normal");
    }

    @Test
    void createReturnsExistingManualObservationWhenExactDuplicateExists() {
        UUID observationId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        UUID testId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID userId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        CreateLabObservationRequest request = new CreateLabObservationRequest(
            testId,
            LocalDate.parse("2026-07-01"),
            new BigDecimal("95.5"),
            "mg/dL",
            new BigDecimal("70"),
            new BigDecimal("99"),
            "normal"
        );
        LabObservation existingObservation = new LabObservation(
            observationId,
            userId,
            testId,
            request.observedAt(),
            new BigDecimal("95.5000"),
            "mg/dL",
            new BigDecimal("70"),
            new BigDecimal("99"),
            "normal"
        );

        when(testCatalogLookupService.findById(testId)).thenReturn(Optional.of(new TestCatalogLookup(testId, "Glucose", "mg/dL")));
        when(defaultUserProvider.getDefaultUserId()).thenReturn(userId);
        when(labObservationRepository.findFirstByUserIdAndTestIdAndObservedAtAndNumericValueAndUnitAndSourceReportIdIsNullAndSourceParsedObservationIdIsNullOrderByIdAsc(
            userId,
            testId,
            request.observedAt(),
            request.numericValue(),
            request.unit()
        )).thenReturn(Optional.of(existingObservation));

        LabObservationResponse response = labObservationService.create(request);

        assertThat(response.id()).isEqualTo(observationId);
        assertThat(response.testId()).isEqualTo(testId);
        assertThat(response.numericValue()).isEqualByComparingTo("95.5000");
        verify(labObservationRepository, never()).save(any(LabObservation.class));
    }

    @Test
    void createReturnsExistingReportObservationForSameSourceParsedObservation() {
        UUID observationId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        UUID testId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID userId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID reportId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID parsedObservationId = UUID.fromString("55555555-5555-5555-5555-555555555555");
        CreateLabObservationCommand command = new CreateLabObservationCommand(
            testId,
            LocalDate.parse("2026-07-08"),
            new BigDecimal("12.8"),
            "g/dL",
            new BigDecimal("12.0"),
            new BigDecimal("15.5"),
            "normal",
            reportId,
            parsedObservationId
        );
        LabObservation existingObservation = new LabObservation(
            observationId,
            userId,
            testId,
            command.observedAt(),
            command.numericValue(),
            "g/dL",
            command.referenceLow(),
            command.referenceHigh(),
            command.abnormalFlag(),
            reportId,
            parsedObservationId
        );

        when(testCatalogLookupService.findById(testId)).thenReturn(Optional.of(new TestCatalogLookup(testId, "Hemoglobin", "g/dL")));
        when(defaultUserProvider.getDefaultUserId()).thenReturn(userId);
        when(labObservationRepository.findFirstByUserIdAndSourceParsedObservationIdOrderByIdAsc(
            userId,
            parsedObservationId
        )).thenReturn(Optional.of(existingObservation));

        LabObservationResponse response = labObservationService.create(command);

        assertThat(response.id()).isEqualTo(observationId);
        assertThat(response.testName()).isEqualTo("Hemoglobin");
        verify(labObservationRepository, never()).save(any(LabObservation.class));
    }

    @Test
    void trendReturnsOrderedPointsAndSummaryValuesForDefaultUser() {
        UUID testId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID userId = UUID.fromString("22222222-2222-2222-2222-222222222222");

        when(testCatalogLookupService.findById(testId)).thenReturn(Optional.of(new TestCatalogLookup(testId, "Hemoglobin", "g/dL")));
        when(defaultUserProvider.getDefaultUserId()).thenReturn(userId);
        when(labObservationRepository.findTrendPointsByUserIdAndTestId(userId, testId)).thenReturn(List.of(
            trendPoint("2026-07-01", "12.0", "g/dL"),
            trendPoint("2026-07-09", "12.8", "g/dL")
        ));

        LabObservationTrendResponse response = labObservationService.trend(testId);

        assertThat(response.testId()).isEqualTo(testId);
        assertThat(response.testName()).isEqualTo("Hemoglobin");
        assertThat(response.unit()).isEqualTo("g/dL");
        assertThat(response.points()).extracting(LabObservationTrendPointResponse::observedAt)
            .containsExactly(LocalDate.parse("2026-07-01"), LocalDate.parse("2026-07-09"));
        assertThat(response.points()).extracting(LabObservationTrendPointResponse::numericValue)
            .containsExactly(new BigDecimal("12.0"), new BigDecimal("12.8"));
        assertThat(response.points()).extracting(LabObservationTrendPointResponse::unit)
            .containsExactly("g/dL", "g/dL");
        assertThat(response.points()).extracting(LabObservationTrendPointResponse::sourceType)
            .containsOnly(LabObservationSourceType.MANUAL);
        assertThat(response.latestValue()).isEqualByComparingTo("12.8");
        assertThat(response.previousValue()).isEqualByComparingTo("12.0");
        assertThat(response.absoluteChange()).isEqualByComparingTo("0.8");
        assertThat(response.percentChange()).isEqualByComparingTo("6.6667");
    }

    @Test
    void trendReturnsEmptySummaryWhenNoObservationsExistForKnownTest() {
        UUID testId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID userId = UUID.fromString("22222222-2222-2222-2222-222222222222");

        when(testCatalogLookupService.findById(testId)).thenReturn(Optional.of(new TestCatalogLookup(testId, "Hemoglobin", "g/dL")));
        when(defaultUserProvider.getDefaultUserId()).thenReturn(userId);
        when(labObservationRepository.findTrendPointsByUserIdAndTestId(userId, testId)).thenReturn(List.of());

        LabObservationTrendResponse response = labObservationService.trend(testId);

        assertThat(response.points()).isEmpty();
        assertThat(response.unit()).isEqualTo("g/dL");
        assertThat(response.latestValue()).isNull();
        assertThat(response.previousValue()).isNull();
        assertThat(response.absoluteChange()).isNull();
        assertThat(response.percentChange()).isNull();
    }

    @Test
    void trendReturnsLatestOnlyWhenOneObservationExists() {
        UUID testId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID userId = UUID.fromString("22222222-2222-2222-2222-222222222222");

        when(testCatalogLookupService.findById(testId)).thenReturn(Optional.of(new TestCatalogLookup(testId, "Hemoglobin", "g/dL")));
        when(defaultUserProvider.getDefaultUserId()).thenReturn(userId);
        when(labObservationRepository.findTrendPointsByUserIdAndTestId(userId, testId)).thenReturn(List.of(
            trendPoint("2026-07-09", "12.8", "g/dL")
        ));

        LabObservationTrendResponse response = labObservationService.trend(testId);

        assertThat(response.latestValue()).isEqualByComparingTo("12.8");
        assertThat(response.previousValue()).isNull();
        assertThat(response.absoluteChange()).isNull();
        assertThat(response.percentChange()).isNull();
    }

    @Test
    void trendSkipsPercentChangeWhenPreviousValueIsZero() {
        UUID testId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID userId = UUID.fromString("22222222-2222-2222-2222-222222222222");

        when(testCatalogLookupService.findById(testId)).thenReturn(Optional.of(new TestCatalogLookup(testId, "Hemoglobin", "g/dL")));
        when(defaultUserProvider.getDefaultUserId()).thenReturn(userId);
        when(labObservationRepository.findTrendPointsByUserIdAndTestId(userId, testId)).thenReturn(List.of(
            trendPoint("2026-07-01", "0", "g/dL"),
            trendPoint("2026-07-09", "12.8", "g/dL")
        ));

        LabObservationTrendResponse response = labObservationService.trend(testId);

        assertThat(response.latestValue()).isEqualByComparingTo("12.8");
        assertThat(response.previousValue()).isEqualByComparingTo("0");
        assertThat(response.absoluteChange()).isEqualByComparingTo("12.8");
        assertThat(response.percentChange()).isNull();
    }

    @Test
    void trendIncludesReportSourceDetailsWhenObservationCameFromParsedReport() {
        UUID testId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID userId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID reportId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID parsedObservationId = UUID.fromString("55555555-5555-5555-5555-555555555555");

        when(testCatalogLookupService.findById(testId)).thenReturn(Optional.of(new TestCatalogLookup(testId, "Hemoglobin", "g/dL")));
        when(defaultUserProvider.getDefaultUserId()).thenReturn(userId);
        when(labObservationRepository.findTrendPointsByUserIdAndTestId(userId, testId)).thenReturn(List.of(new TestTrendPoint(
            LocalDate.parse("2026-07-09"),
            new BigDecimal("12.8"),
            "g/dL",
            reportId,
            "lab-report-july.pdf",
            "Quest Diagnostics",
            LocalDate.parse("2026-07-09"),
            parsedObservationId
        )));

        LabObservationTrendResponse response = labObservationService.trend(testId);

        LabObservationTrendPointResponse point = response.points().getFirst();
        assertThat(point.sourceType()).isEqualTo(LabObservationSourceType.REPORT);
        assertThat(point.reportId()).isEqualTo(reportId);
        assertThat(point.reportOriginalFilename()).isEqualTo("lab-report-july.pdf");
        assertThat(point.labName()).isEqualTo("Quest Diagnostics");
        assertThat(point.reportDate()).isEqualTo(LocalDate.parse("2026-07-09"));
        assertThat(point.parsedObservationId()).isEqualTo(parsedObservationId);
    }

    private static LabObservationTrendPointProjection trendPoint(String observedAt, String numericValue, String unit) {
        return new TestTrendPoint(
            LocalDate.parse(observedAt),
            new BigDecimal(numericValue),
            unit,
            null,
            null,
            null,
            null,
            null
        );
    }

    private record TestTrendPoint(
        LocalDate observedAt,
        BigDecimal numericValue,
        String unit,
        UUID reportId,
        String reportOriginalFilename,
        String labName,
        LocalDate reportDate,
        UUID parsedObservationId
    ) implements LabObservationTrendPointProjection {

        @Override
        public LocalDate getObservedAt() {
            return observedAt;
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
        public UUID getParsedObservationId() {
            return parsedObservationId;
        }
    }
}
