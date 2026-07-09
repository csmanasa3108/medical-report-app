package com.medicalreportapp.testcatalog;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class TestCatalogController {

    private final TestCatalogService testCatalogService;

    TestCatalogController(TestCatalogService testCatalogService) {
        this.testCatalogService = testCatalogService;
    }

    @GetMapping("/api/tests")
    public List<TestCatalogResponse> listTests() {
        return testCatalogService.listTests();
    }
}
