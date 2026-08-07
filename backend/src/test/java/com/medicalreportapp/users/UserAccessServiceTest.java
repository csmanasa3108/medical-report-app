package com.medicalreportapp.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class UserAccessServiceTest {

    @Mock
    private UserContextResolver userContextResolver;

    @Mock
    private PatientClinicianAccessRepository patientClinicianAccessRepository;

    @Test
    void patientReadsOwnDataWhenPatientIdIsOmitted() {
        UUID patientUserId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        UserAccessService userAccessService = new UserAccessService(userContextResolver, patientClinicianAccessRepository);

        when(userContextResolver.getCurrentUser()).thenReturn(user(patientUserId, AppUserRole.PATIENT));

        UUID resolvedPatientId = userAccessService.resolveReadablePatientId(null);

        assertThat(resolvedPatientId).isEqualTo(patientUserId);
        verify(patientClinicianAccessRepository, never()).existsByPatientUserIdAndClinicianUserIdAndStatus(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void patientCannotReadAnotherPatient() {
        UUID patientUserId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        UUID otherPatientUserId = UUID.fromString("00000000-0000-0000-0000-000000000103");
        UserAccessService userAccessService = new UserAccessService(userContextResolver, patientClinicianAccessRepository);

        when(userContextResolver.getCurrentUser()).thenReturn(user(patientUserId, AppUserRole.PATIENT));

        assertThatThrownBy(() -> userAccessService.resolveReadablePatientId(otherPatientUserId))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
            .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void clinicianRequiresPatientIdForPatientScopedReads() {
        UUID clinicianUserId = UUID.fromString("00000000-0000-0000-0000-000000000102");
        UserAccessService userAccessService = new UserAccessService(userContextResolver, patientClinicianAccessRepository);

        when(userContextResolver.getCurrentUser()).thenReturn(user(clinicianUserId, AppUserRole.CLINICIAN));

        assertThatThrownBy(() -> userAccessService.resolveReadablePatientId(null))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void clinicianCanReadAssignedPatient() {
        UUID patientUserId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        UUID clinicianUserId = UUID.fromString("00000000-0000-0000-0000-000000000102");
        UserAccessService userAccessService = new UserAccessService(userContextResolver, patientClinicianAccessRepository);

        when(userContextResolver.getCurrentUser()).thenReturn(user(clinicianUserId, AppUserRole.CLINICIAN));
        when(patientClinicianAccessRepository.existsByPatientUserIdAndClinicianUserIdAndStatus(
            patientUserId,
            clinicianUserId,
            PatientClinicianAccessStatus.ACTIVE
        )).thenReturn(true);

        UUID resolvedPatientId = userAccessService.resolveReadablePatientId(patientUserId);

        assertThat(resolvedPatientId).isEqualTo(patientUserId);
    }

    @Test
    void clinicianCannotReadUnassignedPatient() {
        UUID patientUserId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        UUID clinicianUserId = UUID.fromString("00000000-0000-0000-0000-000000000102");
        UserAccessService userAccessService = new UserAccessService(userContextResolver, patientClinicianAccessRepository);

        when(userContextResolver.getCurrentUser()).thenReturn(user(clinicianUserId, AppUserRole.CLINICIAN));
        when(patientClinicianAccessRepository.existsByPatientUserIdAndClinicianUserIdAndStatus(
            patientUserId,
            clinicianUserId,
            PatientClinicianAccessStatus.ACTIVE
        )).thenReturn(false);

        assertThatThrownBy(() -> userAccessService.resolveReadablePatientId(patientUserId))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
            .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void clinicianCannotWritePatientData() {
        UUID clinicianUserId = UUID.fromString("00000000-0000-0000-0000-000000000102");
        UserAccessService userAccessService = new UserAccessService(userContextResolver, patientClinicianAccessRepository);

        when(userContextResolver.getCurrentUser()).thenReturn(user(clinicianUserId, AppUserRole.CLINICIAN));

        assertThatThrownBy(userAccessService::requireCurrentUserCanWritePatientData)
            .isInstanceOf(ResponseStatusException.class)
            .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
            .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void clinicianRoleCanUseClinicianEndpoints() {
        UUID clinicianUserId = UUID.fromString("00000000-0000-0000-0000-000000000102");
        UserAccessService userAccessService = new UserAccessService(userContextResolver, patientClinicianAccessRepository);

        AppUser clinician = user(clinicianUserId, AppUserRole.CLINICIAN);
        when(userContextResolver.getCurrentUser()).thenReturn(clinician);

        assertThat(userAccessService.requireCurrentClinician()).isEqualTo(clinician);
    }

    @Test
    void patientRoleCannotUseClinicianEndpoints() {
        UUID patientUserId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        UserAccessService userAccessService = new UserAccessService(userContextResolver, patientClinicianAccessRepository);

        when(userContextResolver.getCurrentUser()).thenReturn(user(patientUserId, AppUserRole.PATIENT));

        assertThatThrownBy(userAccessService::requireCurrentClinician)
            .isInstanceOf(ResponseStatusException.class)
            .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
            .isEqualTo(HttpStatus.FORBIDDEN);
    }

    private static AppUser user(UUID userId, AppUserRole role) {
        return new AppUser(userId, "test-" + userId + "@example.local", "Test User", role);
    }
}
