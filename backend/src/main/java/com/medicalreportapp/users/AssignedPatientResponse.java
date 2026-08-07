package com.medicalreportapp.users;

import java.time.LocalDate;
import java.util.UUID;

public record AssignedPatientResponse(
    UUID patientId,
    String displayName,
    String email,
    String role,
    String accessStatus,
    Long reportCount,
    LocalDate latestReportDate
) {

    static AssignedPatientResponse from(AssignedPatientProjection patient) {
        return new AssignedPatientResponse(
            patient.getPatientId(),
            patient.getDisplayName(),
            patient.getEmail(),
            patient.getRole(),
            patient.getAccessStatus(),
            patient.getReportCount(),
            patient.getLatestReportDate()
        );
    }
}
