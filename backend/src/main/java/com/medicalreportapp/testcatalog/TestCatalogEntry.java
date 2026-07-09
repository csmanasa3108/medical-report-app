package com.medicalreportapp.testcatalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "test_catalog")
class TestCatalogEntry {

    @Id
    private UUID id;

    @Column(name = "canonical_name", nullable = false, length = 255)
    private String canonicalName;

    @Column(name = "display_name", nullable = false, length = 255)
    private String displayName;

    @Column(name = "default_unit", length = 32)
    private String defaultUnit;

    @Column(name = "category", length = 100)
    private String category;

    protected TestCatalogEntry() {
    }

    TestCatalogEntry(UUID id, String canonicalName, String displayName, String defaultUnit, String category) {
        this.id = id;
        this.canonicalName = canonicalName;
        this.displayName = displayName;
        this.defaultUnit = defaultUnit;
        this.category = category;
    }

    UUID getId() {
        return id;
    }

    String getCanonicalName() {
        return canonicalName;
    }

    String getDisplayName() {
        return displayName;
    }

    String getDefaultUnit() {
        return defaultUnit;
    }

    String getCategory() {
        return category;
    }
}
