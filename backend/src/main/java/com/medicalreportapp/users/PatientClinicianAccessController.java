package com.medicalreportapp.users;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class PatientClinicianAccessController {

    private final PatientClinicianAccessService patientClinicianAccessService;

    PatientClinicianAccessController(PatientClinicianAccessService patientClinicianAccessService) {
        this.patientClinicianAccessService = patientClinicianAccessService;
    }

    @GetMapping("/api/patient/clinician-access")
    public List<PatientClinicianAccessResponse> findClinicianAccess() {
        return patientClinicianAccessService.findClinicianAccessForCurrentPatient();
    }

    @PostMapping("/api/patient/clinician-access")
    @ResponseStatus(HttpStatus.CREATED)
    public PatientClinicianAccessResponse grantClinicianAccess(
        @Valid @RequestBody GrantClinicianAccessRequest request
    ) {
        return patientClinicianAccessService.grantClinicianAccess(request);
    }

    @PatchMapping("/api/patient/clinician-access/{accessId}/revoke")
    public PatientClinicianAccessResponse revokeClinicianAccess(@PathVariable UUID accessId) {
        return patientClinicianAccessService.revokeClinicianAccess(accessId);
    }
}
