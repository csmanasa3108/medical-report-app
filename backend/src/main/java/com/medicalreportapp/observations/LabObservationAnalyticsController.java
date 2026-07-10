package com.medicalreportapp.observations;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
class LabObservationAnalyticsController {

    private final LabObservationService labObservationService;

    LabObservationAnalyticsController(LabObservationService labObservationService) {
        this.labObservationService = labObservationService;
    }

    @GetMapping("/api/analytics/tests/{testId}/trend")
    public LabObservationTrendResponse trend(@PathVariable UUID testId) {
        return labObservationService.trend(testId);
    }
}
