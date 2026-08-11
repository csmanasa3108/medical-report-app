package com.medicalreportapp.users;

import java.time.Instant;
import java.util.UUID;

public record PatientClinicianAccessResponse(
    UUID accessId,
    UUID clinicianUserId,
    String clinicianDisplayName,
    String clinicianEmail,
    String status,
    Instant createdAt
) {

    static PatientClinicianAccessResponse from(PatientClinicianAccessProjection access) {
        return new PatientClinicianAccessResponse(
            access.getAccessId(),
            access.getClinicianUserId(),
            access.getClinicianDisplayName(),
            access.getClinicianEmail(),
            access.getStatus(),
            access.getCreatedAt()
        );
    }

    static PatientClinicianAccessResponse from(PatientClinicianAccess access, AppUser clinician) {
        return new PatientClinicianAccessResponse(
            access.getId(),
            clinician.getId(),
            clinician.getDisplayName(),
            clinician.getEmail(),
            access.getStatus().name(),
            access.getCreatedAt()
        );
    }
}
