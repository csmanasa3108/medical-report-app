package com.medicalreportapp.testcatalog;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TestCatalogLookupService {

    private final TestCatalogRepository testCatalogRepository;

    TestCatalogLookupService(TestCatalogRepository testCatalogRepository) {
        this.testCatalogRepository = testCatalogRepository;
    }

    @Transactional(readOnly = true)
    public Optional<TestCatalogLookup> findById(UUID id) {
        return testCatalogRepository.findById(id)
            .map(entry -> new TestCatalogLookup(entry.getId(), entry.getDisplayName()));
    }
}
