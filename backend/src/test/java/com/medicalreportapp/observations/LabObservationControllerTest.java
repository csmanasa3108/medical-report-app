package com.medicalreportapp.observations;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(LabObservationController.class)
class LabObservationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LabObservationService labObservationService;

    @Test
    void createReturnsCreatedObservation() throws Exception {
        UUID observationId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID testId = UUID.fromString("11111111-1111-1111-1111-111111111111");

        when(labObservationService.create(any(CreateLabObservationRequest.class))).thenReturn(new LabObservationResponse(
            observationId,
            testId,
            "Glucose",
            LocalDate.parse("2026-07-01"),
            new BigDecimal("95.5"),
            "mg/dL",
            new BigDecimal("70"),
            new BigDecimal("99"),
            "normal"
        ));

        mockMvc.perform(post("/api/observations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "testId": "11111111-1111-1111-1111-111111111111",
                      "observedAt": "2026-07-01",
                      "numericValue": 95.5,
                      "unit": "mg/dL",
                      "referenceLow": 70,
                      "referenceHigh": 99,
                      "abnormalFlag": "normal"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(content().contentTypeCompatibleWith("application/json"))
            .andExpect(jsonPath("$.id").value(observationId.toString()))
            .andExpect(jsonPath("$.testId").value(testId.toString()))
            .andExpect(jsonPath("$.testName").value("Glucose"))
            .andExpect(jsonPath("$.observedAt").value("2026-07-01"))
            .andExpect(jsonPath("$.numericValue").value(95.5))
            .andExpect(jsonPath("$.unit").value("mg/dL"))
            .andExpect(jsonPath("$.referenceLow").value(70))
            .andExpect(jsonPath("$.referenceHigh").value(99))
            .andExpect(jsonPath("$.abnormalFlag").value("normal"));

        verify(labObservationService).create(any(CreateLabObservationRequest.class));
    }

    @Test
    void createRejectsMissingRequiredFields() throws Exception {
        mockMvc.perform(post("/api/observations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "testId": "11111111-1111-1111-1111-111111111111",
                      "numericValue": 95.5,
                      "unit": "mg/dL",
                      "referenceLow": 70,
                      "referenceHigh": 99,
                      "abnormalFlag": "normal"
                    }
                    """))
            .andExpect(status().isBadRequest());
    }
}
