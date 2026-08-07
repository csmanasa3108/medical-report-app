package com.medicalreportapp.users;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface PatientClinicianAccessRepository extends JpaRepository<PatientClinicianAccess, UUID> {

    boolean existsByPatientUserIdAndClinicianUserIdAndStatus(
        UUID patientUserId,
        UUID clinicianUserId,
        PatientClinicianAccessStatus status
    );
}
