package com.medicalreportapp.observations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.medicalreportapp.testcatalog.TestCatalogLookup;
import com.medicalreportapp.testcatalog.TestCatalogLookupService;
import java.math.BigDecimal;
import java.time.LocalDate;
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

        when(testCatalogLookupService.findById(testId)).thenReturn(Optional.of(new TestCatalogLookup(testId, "Glucose")));
        when(defaultUserProvider.getDefaultUserId()).thenReturn(userId);
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
}
