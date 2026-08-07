package com.medicalreportapp.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ClinicianPatientServiceTest {

    @Mock
    private UserAccessService userAccessService;

    @Mock
    private PatientClinicianAccessRepository patientClinicianAccessRepository;

    @Test
    void returnsAssignedPatientsForCurrentClinician() {
        UUID clinicianUserId = UUID.fromString("00000000-0000-0000-0000-000000000102");
        UUID patientUserId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        ClinicianPatientService service = new ClinicianPatientService(
            userAccessService,
            patientClinicianAccessRepository
        );

        when(userAccessService.requireCurrentClinician()).thenReturn(user(clinicianUserId, AppUserRole.CLINICIAN));
        when(patientClinicianAccessRepository.findAssignedPatientsForClinician(
            clinicianUserId,
            PatientClinicianAccessStatus.ACTIVE.name(),
            AppUserRole.PATIENT.name()
        )).thenReturn(List.of(new TestAssignedPatientProjection(
            patientUserId,
            "Demo Patient",
            "patient.demo@soverahealth.local",
            "PATIENT",
            "ACTIVE",
            3L,
            LocalDate.parse("2026-08-06")
        )));

        List<AssignedPatientResponse> patients = service.findAssignedPatientsForCurrentClinician();

        assertThat(patients).containsExactly(new AssignedPatientResponse(
            patientUserId,
            "Demo Patient",
            "patient.demo@soverahealth.local",
            "PATIENT",
            "ACTIVE",
            3L,
            LocalDate.parse("2026-08-06")
        ));
    }

    @Test
    void rejectsNonClinicianCurrentUser() {
        ClinicianPatientService service = new ClinicianPatientService(
            userAccessService,
            patientClinicianAccessRepository
        );

        when(userAccessService.requireCurrentClinician()).thenThrow(new ResponseStatusException(
            HttpStatus.FORBIDDEN,
            "Only clinicians can view assigned patients"
        ));

        assertThatThrownBy(service::findAssignedPatientsForCurrentClinician)
            .isInstanceOf(ResponseStatusException.class)
            .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
            .isEqualTo(HttpStatus.FORBIDDEN);
        verify(patientClinicianAccessRepository, never()).findAssignedPatientsForClinician(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any()
        );
    }

    private static AppUser user(UUID userId, AppUserRole role) {
        return new AppUser(userId, "test-" + userId + "@example.local", "Test User", role);
    }

    private record TestAssignedPatientProjection(
        UUID patientId,
        String displayName,
        String email,
        String role,
        String accessStatus,
        Long reportCount,
        LocalDate latestReportDate
    ) implements AssignedPatientProjection {

        @Override
        public UUID getPatientId() {
            return patientId;
        }

        @Override
        public String getDisplayName() {
            return displayName;
        }

        @Override
        public String getEmail() {
            return email;
        }

        @Override
        public String getRole() {
            return role;
        }

        @Override
        public String getAccessStatus() {
            return accessStatus;
        }

        @Override
        public Long getReportCount() {
            return reportCount;
        }

        @Override
        public LocalDate getLatestReportDate() {
            return latestReportDate;
        }
    }
}
