package com.medicalreportapp.users;

import java.time.LocalDate;
import java.util.UUID;

interface AssignedPatientProjection {

    UUID getPatientId();

    String getDisplayName();

    String getEmail();

    String getRole();

    String getAccessStatus();

    Long getReportCount();

    LocalDate getLatestReportDate();
}
