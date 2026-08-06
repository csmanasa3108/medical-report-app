package com.medicalreportapp.observations;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface LabObservationRepository extends JpaRepository<LabObservation, UUID> {

    Optional<LabObservation> findByIdAndUserId(UUID id, UUID userId);

    List<LabObservation> findByUserIdAndTestIdOrderByObservedAtAsc(UUID userId, UUID testId);
}
