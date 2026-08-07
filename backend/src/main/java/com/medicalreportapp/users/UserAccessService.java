package com.medicalreportapp.users;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UserAccessService {

    private final UserContextResolver userContextResolver;
    private final PatientClinicianAccessRepository patientClinicianAccessRepository;

    UserAccessService(
        UserContextResolver userContextResolver,
        PatientClinicianAccessRepository patientClinicianAccessRepository
    ) {
        this.userContextResolver = userContextResolver;
        this.patientClinicianAccessRepository = patientClinicianAccessRepository;
    }

    public UUID getCurrentUserId() {
        return userContextResolver.getCurrentUser().getId();
    }

    public UUID resolveReadablePatientId(UUID requestedPatientId) {
        AppUser currentUser = userContextResolver.getCurrentUser();
        if (currentUser.getRole() == AppUserRole.PATIENT) {
            if (requestedPatientId == null || requestedPatientId.equals(currentUser.getId())) {
                return currentUser.getId();
            }
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Patients can access only their own data");
        }

        if (currentUser.getRole() == AppUserRole.CLINICIAN) {
            if (requestedPatientId == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "patientId is required for clinician access");
            }
            requireClinicianPatientAccess(currentUser.getId(), requestedPatientId);
            return requestedPatientId;
        }

        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Unsupported user role");
    }

    public void requireCurrentUserCanReadPatientData(UUID patientUserId) {
        resolveReadablePatientId(patientUserId);
    }

    public UUID requireCurrentUserCanWritePatientData() {
        AppUser currentUser = userContextResolver.getCurrentUser();
        if (currentUser.getRole() != AppUserRole.PATIENT) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only patients can modify patient data");
        }
        return currentUser.getId();
    }

    public void requireCurrentUserCanWritePatientData(UUID patientUserId) {
        UUID currentPatientUserId = requireCurrentUserCanWritePatientData();
        if (!currentPatientUserId.equals(patientUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Patients can modify only their own data");
        }
    }

    private void requireClinicianPatientAccess(UUID clinicianUserId, UUID patientUserId) {
        boolean hasAccess = patientClinicianAccessRepository.existsByPatientUserIdAndClinicianUserIdAndStatus(
            patientUserId,
            clinicianUserId,
            PatientClinicianAccessStatus.ACTIVE
        );
        if (!hasAccess) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Clinician is not assigned to this patient");
        }
    }
}
