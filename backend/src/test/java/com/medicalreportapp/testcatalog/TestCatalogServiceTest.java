package com.medicalreportapp.testcatalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TestCatalogServiceTest {

    @Mock
    private TestCatalogRepository testCatalogRepository;

    @InjectMocks
    private TestCatalogService testCatalogService;

    @Test
    void listTestsReturnsRepositoryResultsAsResponses() {
        UUID glucoseId = UUID.fromString("11111111-1111-1111-1111-111111111111");

        when(testCatalogRepository.findAllByOrderByDisplayNameAsc()).thenReturn(List.of(
            new TestCatalogEntry(glucoseId, "glucose", "Glucose", "mg/dL", "Metabolic")
        ));

        List<TestCatalogResponse> responses = testCatalogService.listTests();

        assertThat(responses).containsExactly(
            new TestCatalogResponse(glucoseId, "glucose", "Glucose", "mg/dL", "Metabolic")
        );
        verify(testCatalogRepository).findAllByOrderByDisplayNameAsc();
    }
}
