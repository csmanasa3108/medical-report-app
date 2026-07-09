package com.medicalreportapp.testcatalog;

import java.util.UUID;

public record TestCatalogResponse(
    UUID id,
    String canonicalName,
    String displayName,
    String defaultUnit,
    String category
) {

    static TestCatalogResponse from(TestCatalogEntry entry) {
        return new TestCatalogResponse(
            entry.getId(),
            entry.getCanonicalName(),
            entry.getDisplayName(),
            entry.getDefaultUnit(),
            entry.getCategory()
        );
    }
}
