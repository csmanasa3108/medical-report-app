package com.medicalreportapp.users;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ClinicianPatientController {

    private final ClinicianPatientService clinicianPatientService;

    ClinicianPatientController(ClinicianPatientService clinicianPatientService) {
        this.clinicianPatientService = clinicianPatientService;
    }

    @GetMapping("/api/clinician/patients")
    public List<AssignedPatientResponse> findAssignedPatients() {
        return clinicianPatientService.findAssignedPatientsForCurrentClinician();
    }
}
