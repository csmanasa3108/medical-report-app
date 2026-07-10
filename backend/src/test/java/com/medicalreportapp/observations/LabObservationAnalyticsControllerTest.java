package com.medicalreportapp.observations;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
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

@WebMvcTest(LabObservationAnalyticsController.class)
class LabObservationAnalyticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LabObservationService labObservationService;

    @Test
    void trendReturnsObservationTrend() throws Exception {
        UUID testId = UUID.fromString("11111111-1111-1111-1111-111111111111");

        when(labObservationService.trend(testId)).thenReturn(new LabObservationTrendResponse(
            testId,
            "Hemoglobin",
            "g/dL",
            List.of(
                new LabObservationTrendPointResponse(LocalDate.parse("2026-07-01"), new BigDecimal("12.0")),
                new LabObservationTrendPointResponse(LocalDate.parse("2026-07-09"), new BigDecimal("12.8"))
            ),
            new BigDecimal("12.8"),
            new BigDecimal("12.0"),
            new BigDecimal("0.8"),
            new BigDecimal("6.6667")
        ));

        mockMvc.perform(get("/api/analytics/tests/{testId}/trend", testId))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("application/json"))
            .andExpect(jsonPath("$.testId").value(testId.toString()))
            .andExpect(jsonPath("$.testName").value("Hemoglobin"))
            .andExpect(jsonPath("$.unit").value("g/dL"))
            .andExpect(jsonPath("$.points[0].date").value("2026-07-01"))
            .andExpect(jsonPath("$.points[0].value").value(12.0))
            .andExpect(jsonPath("$.points[1].date").value("2026-07-09"))
            .andExpect(jsonPath("$.points[1].value").value(12.8))
            .andExpect(jsonPath("$.latestValue").value(12.8))
            .andExpect(jsonPath("$.previousValue").value(12.0))
            .andExpect(jsonPath("$.absoluteChange").value(0.8))
            .andExpect(jsonPath("$.percentChange").value(6.6667));
    }

    @Test
    void trendReturnsNotFoundForUnknownTest() throws Exception {
        UUID testId = UUID.fromString("99999999-9999-9999-9999-999999999999");

        when(labObservationService.trend(testId)).thenThrow(new ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "Test catalog entry not found"
        ));

        mockMvc.perform(get("/api/analytics/tests/{testId}/trend", testId))
            .andExpect(status().isNotFound());
    }
}
