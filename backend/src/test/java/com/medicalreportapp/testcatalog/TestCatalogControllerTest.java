package com.medicalreportapp.testcatalog;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TestCatalogController.class)
class TestCatalogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TestCatalogService testCatalogService;

    @Test
    void listTestsReturnsCatalogResponses() throws Exception {
        UUID glucoseId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID hemoglobinId = UUID.fromString("22222222-2222-2222-2222-222222222222");

        when(testCatalogService.listTests()).thenReturn(List.of(
            new TestCatalogResponse(glucoseId, "glucose", "Glucose", "mg/dL", "Metabolic"),
            new TestCatalogResponse(hemoglobinId, "hemoglobin", "Hemoglobin", "g/dL", "Hematology")
        ));

        mockMvc.perform(get("/api/tests"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("application/json"))
            .andExpect(jsonPath("$[0].id").value(glucoseId.toString()))
            .andExpect(jsonPath("$[0].canonicalName").value("glucose"))
            .andExpect(jsonPath("$[0].displayName").value("Glucose"))
            .andExpect(jsonPath("$[0].defaultUnit").value("mg/dL"))
            .andExpect(jsonPath("$[0].category").value("Metabolic"))
            .andExpect(jsonPath("$[1].id").value(hemoglobinId.toString()))
            .andExpect(jsonPath("$[1].displayName").value("Hemoglobin"));
    }
}
