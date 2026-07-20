package com.medicalreportapp.testcatalog;

import java.util.UUID;

public record TestCatalogMatch(
    UUID id,
    String canonicalName,
    String displayName,
    String defaultUnit
) {
}
