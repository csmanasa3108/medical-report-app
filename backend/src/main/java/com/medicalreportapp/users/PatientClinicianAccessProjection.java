package com.medicalreportapp.users;

import java.time.Instant;
import java.util.UUID;

interface PatientClinicianAccessProjection {

    UUID getAccessId();

    UUID getClinicianUserId();

    String getClinicianDisplayName();

    String getClinicianEmail();

    String getStatus();

    Instant getCreatedAt();
}
