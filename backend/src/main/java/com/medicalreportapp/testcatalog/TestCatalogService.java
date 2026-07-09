package com.medicalreportapp.testcatalog;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class TestCatalogService {

    private final TestCatalogRepository testCatalogRepository;

    TestCatalogService(TestCatalogRepository testCatalogRepository) {
        this.testCatalogRepository = testCatalogRepository;
    }

    @Transactional(readOnly = true)
    public List<TestCatalogResponse> listTests() {
        return testCatalogRepository.findAllByOrderByDisplayNameAsc()
            .stream()
            .map(TestCatalogResponse::from)
            .toList();
    }
}
