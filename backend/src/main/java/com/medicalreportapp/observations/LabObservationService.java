package com.medicalreportapp.observations;

import com.medicalreportapp.testcatalog.TestCatalogLookup;
import com.medicalreportapp.testcatalog.TestCatalogLookupService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
class LabObservationService {

    private final LabObservationRepository labObservationRepository;
    private final TestCatalogLookupService testCatalogLookupService;
    private final DefaultUserProvider defaultUserProvider;

    LabObservationService(
        LabObservationRepository labObservationRepository,
        TestCatalogLookupService testCatalogLookupService,
        DefaultUserProvider defaultUserProvider
    ) {
        this.labObservationRepository = labObservationRepository;
        this.testCatalogLookupService = testCatalogLookupService;
        this.defaultUserProvider = defaultUserProvider;
    }

    @Transactional
    public LabObservationResponse create(CreateLabObservationRequest request) {
        TestCatalogLookup test = testCatalogLookupService.findById(request.testId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Test catalog entry not found"));

        LabObservation observation = new LabObservation(
            UUID.randomUUID(),
            defaultUserProvider.getDefaultUserId(),
            test.id(),
            request.observedAt(),
            request.numericValue(),
            request.unit(),
            request.referenceLow(),
            request.referenceHigh(),
            request.abnormalFlag()
        );

        LabObservation savedObservation = labObservationRepository.save(observation);
        return LabObservationResponse.from(savedObservation, test.displayName());
    }

    @Transactional(readOnly = true)
    public LabObservationTrendResponse trend(UUID testId) {
        TestCatalogLookup test = testCatalogLookupService.findById(testId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Test catalog entry not found"));

        List<LabObservation> observations = labObservationRepository.findByUserIdAndTestIdOrderByObservedAtAsc(
            defaultUserProvider.getDefaultUserId(),
            test.id()
        );

        List<LabObservationTrendPointResponse> points = observations.stream()
            .map(observation -> new LabObservationTrendPointResponse(
                observation.getObservedAt(),
                observation.getNumericValue()
            ))
            .toList();

        if (observations.isEmpty()) {
            return new LabObservationTrendResponse(
                test.id(),
                test.displayName(),
                test.defaultUnit(),
                points,
                null,
                null,
                null,
                null
            );
        }

        LabObservation latest = observations.getLast();
        BigDecimal latestValue = latest.getNumericValue();

        if (observations.size() == 1) {
            return new LabObservationTrendResponse(
                test.id(),
                test.displayName(),
                latest.getUnit(),
                points,
                latestValue,
                null,
                null,
                null
            );
        }

        BigDecimal previousValue = observations.get(observations.size() - 2).getNumericValue();
        BigDecimal absoluteChange = latestValue.subtract(previousValue);
        BigDecimal percentChange = null;
        if (previousValue.compareTo(BigDecimal.ZERO) != 0) {
            percentChange = absoluteChange
                .multiply(new BigDecimal("100"))
                .divide(previousValue, 4, RoundingMode.HALF_UP);
        }

        return new LabObservationTrendResponse(
            test.id(),
            test.displayName(),
            latest.getUnit(),
            points,
            latestValue,
            previousValue,
            absoluteChange,
            percentChange
        );
    }
}
