package com.medicalreportapp.users;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ClinicianPatientService {

    private final UserAccessService userAccessService;
    private final PatientClinicianAccessRepository patientClinicianAccessRepository;

    ClinicianPatientService(
        UserAccessService userAccessService,
        PatientClinicianAccessRepository patientClinicianAccessRepository
    ) {
        this.userAccessService = userAccessService;
        this.patientClinicianAccessRepository = patientClinicianAccessRepository;
    }

    @Transactional(readOnly = true)
    public List<AssignedPatientResponse> findAssignedPatientsForCurrentClinician() {
        AppUser clinician = userAccessService.requireCurrentClinician();

        return patientClinicianAccessRepository.findAssignedPatientsForClinician(
                clinician.getId(),
                PatientClinicianAccessStatus.ACTIVE.name(),
                AppUserRole.PATIENT.name()
            )
            .stream()
            .map(AssignedPatientResponse::from)
            .toList();
    }
}
