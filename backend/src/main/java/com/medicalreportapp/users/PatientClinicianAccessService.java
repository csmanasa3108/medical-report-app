package com.medicalreportapp.users;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
class PatientClinicianAccessService {

    private final UserAccessService userAccessService;
    private final AppUserRepository appUserRepository;
    private final PatientClinicianAccessRepository patientClinicianAccessRepository;

    PatientClinicianAccessService(
        UserAccessService userAccessService,
        AppUserRepository appUserRepository,
        PatientClinicianAccessRepository patientClinicianAccessRepository
    ) {
        this.userAccessService = userAccessService;
        this.appUserRepository = appUserRepository;
        this.patientClinicianAccessRepository = patientClinicianAccessRepository;
    }

    @Transactional(readOnly = true)
    public List<PatientClinicianAccessResponse> findClinicianAccessForCurrentPatient() {
        UUID patientUserId = userAccessService.requireCurrentUserCanWritePatientData();

        return patientClinicianAccessRepository.findClinicianAccessForPatient(
                patientUserId,
                AppUserRole.CLINICIAN.name(),
                PatientClinicianAccessStatus.ACTIVE.name()
            )
            .stream()
            .map(PatientClinicianAccessResponse::from)
            .toList();
    }

    @Transactional
    public PatientClinicianAccessResponse grantClinicianAccess(GrantClinicianAccessRequest request) {
        UUID patientUserId = userAccessService.requireCurrentUserCanWritePatientData();
        String clinicianEmail = normalizedEmail(request.clinicianEmail());
        AppUser clinician = appUserRepository.findByEmail(clinicianEmail)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Clinician not found"));
        if (clinician.getRole() != AppUserRole.CLINICIAN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User is not a clinician");
        }

        PatientClinicianAccess access = patientClinicianAccessRepository
            .findFirstByPatientUserIdAndClinicianUserIdOrderByCreatedAtAsc(patientUserId, clinician.getId())
            .orElseGet(() -> new PatientClinicianAccess(
                UUID.randomUUID(),
                patientUserId,
                clinician.getId(),
                PatientClinicianAccessStatus.ACTIVE
            ));
        access.activate();

        PatientClinicianAccess savedAccess = patientClinicianAccessRepository.saveAndFlush(access);
        return PatientClinicianAccessResponse.from(savedAccess, clinician);
    }

    @Transactional
    public PatientClinicianAccessResponse revokeClinicianAccess(UUID accessId) {
        UUID patientUserId = userAccessService.requireCurrentUserCanWritePatientData();
        PatientClinicianAccess access = patientClinicianAccessRepository.findByIdAndPatientUserId(accessId, patientUserId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Clinician access not found"));
        PatientClinicianAccess responseAccess = access;
        if (access.getStatus() != PatientClinicianAccessStatus.INACTIVE) {
            access.revoke();
            responseAccess = patientClinicianAccessRepository.saveAndFlush(access);
        }
        AppUser clinician = appUserRepository.findById(responseAccess.getClinicianUserId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Clinician not found"));

        return PatientClinicianAccessResponse.from(responseAccess, clinician);
    }

    private String normalizedEmail(String email) {
        if (!StringUtils.hasText(email)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "clinicianEmail is required");
        }

        return email.trim();
    }
}
