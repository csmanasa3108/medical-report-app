package com.medicalreportapp.observations;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface LabObservationRepository extends JpaRepository<LabObservation, UUID> {

    List<LabObservation> findByUserIdAndTestIdOrderByObservedAtAsc(UUID userId, UUID testId);
}
