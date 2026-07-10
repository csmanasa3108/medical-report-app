package com.medicalreportapp.observations;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class LabObservationController {

    private final LabObservationService labObservationService;

    LabObservationController(LabObservationService labObservationService) {
        this.labObservationService = labObservationService;
    }

    @PostMapping("/api/observations")
    @ResponseStatus(HttpStatus.CREATED)
    public LabObservationResponse create(@Valid @RequestBody CreateLabObservationRequest request) {
        return labObservationService.create(request);
    }
}
