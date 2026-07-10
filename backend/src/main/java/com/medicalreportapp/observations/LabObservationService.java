package com.medicalreportapp.observations;

import com.medicalreportapp.testcatalog.TestCatalogLookup;
import com.medicalreportapp.testcatalog.TestCatalogLookupService;
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
}
