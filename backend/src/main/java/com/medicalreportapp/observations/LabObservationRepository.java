package com.medicalreportapp.observations;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface LabObservationRepository extends JpaRepository<LabObservation, UUID> {
}
