package com.medicalreportapp.users;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

@WebMvcTest(ClinicianPatientController.class)
class ClinicianPatientControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ClinicianPatientService clinicianPatientService;

    @Test
    void findAssignedPatientsReturnsPatientSummaries() throws Exception {
        UUID patientUserId = UUID.fromString("00000000-0000-0000-0000-000000000101");

        when(clinicianPatientService.findAssignedPatientsForCurrentClinician()).thenReturn(List.of(
            new AssignedPatientResponse(
                patientUserId,
                "Demo Patient",
                "patient.demo@soverahealth.local",
                "PATIENT",
                "ACTIVE",
                2L,
                LocalDate.parse("2026-08-06")
            )
        ));

        mockMvc.perform(get("/api/clinician/patients"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].patientId").value(patientUserId.toString()))
            .andExpect(jsonPath("$[0].displayName").value("Demo Patient"))
            .andExpect(jsonPath("$[0].email").value("patient.demo@soverahealth.local"))
            .andExpect(jsonPath("$[0].role").value("PATIENT"))
            .andExpect(jsonPath("$[0].accessStatus").value("ACTIVE"))
            .andExpect(jsonPath("$[0].reportCount").value(2))
            .andExpect(jsonPath("$[0].latestReportDate").value("2026-08-06"));
    }

    @Test
    void findAssignedPatientsReturnsForbiddenForNonClinician() throws Exception {
        when(clinicianPatientService.findAssignedPatientsForCurrentClinician())
            .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Only clinicians can view assigned patients"));

        mockMvc.perform(get("/api/clinician/patients"))
            .andExpect(status().isForbidden());
    }
}
