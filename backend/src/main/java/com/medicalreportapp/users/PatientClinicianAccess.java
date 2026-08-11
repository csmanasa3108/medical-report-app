package com.medicalreportapp.users;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "patient_clinician_access")
public class PatientClinicianAccess {

    @Id
    private UUID id;

    @Column(name = "patient_user_id", nullable = false)
    private UUID patientUserId;

    @Column(name = "clinician_user_id", nullable = false)
    private UUID clinicianUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private PatientClinicianAccessStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PatientClinicianAccess() {
    }

    PatientClinicianAccess(
        UUID id,
        UUID patientUserId,
        UUID clinicianUserId,
        PatientClinicianAccessStatus status
    ) {
        this.id = id;
        this.patientUserId = patientUserId;
        this.clinicianUserId = clinicianUserId;
        this.status = status;
    }

    public UUID getId() {
        return id;
    }

    public UUID getPatientUserId() {
        return patientUserId;
    }

    public UUID getClinicianUserId() {
        return clinicianUserId;
    }

    public PatientClinicianAccessStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    void activate() {
        this.status = PatientClinicianAccessStatus.ACTIVE;
    }

    void revoke() {
        this.status = PatientClinicianAccessStatus.REVOKED;
    }
}
