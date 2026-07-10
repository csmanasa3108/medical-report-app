package com.medicalreportapp.testcatalog;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.medicalreportapp.config.WebCorsConfig;

@WebMvcTest(TestCatalogController.class)
@Import(WebCorsConfig.class)
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

        mockMvc.perform(get("/api/tests")
                .header(HttpHeaders.ORIGIN, "http://localhost:5173"))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"))
            .andExpect(content().contentTypeCompatibleWith("application/json"))
            .andExpect(jsonPath("$[0].id").value(glucoseId.toString()))
            .andExpect(jsonPath("$[0].canonicalName").value("glucose"))
            .andExpect(jsonPath("$[0].displayName").value("Glucose"))
            .andExpect(jsonPath("$[0].defaultUnit").value("mg/dL"))
            .andExpect(jsonPath("$[0].category").value("Metabolic"))
            .andExpect(jsonPath("$[1].id").value(hemoglobinId.toString()))
            .andExpect(jsonPath("$[1].displayName").value("Hemoglobin"));
    }

    @Test
    void permitsLocalFrontendCorsPreflight() throws Exception {
        mockMvc.perform(options("/api/tests")
                .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type"))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"))
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, containsString("GET")))
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, containsString("POST")))
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, "Content-Type"));
    }
}
