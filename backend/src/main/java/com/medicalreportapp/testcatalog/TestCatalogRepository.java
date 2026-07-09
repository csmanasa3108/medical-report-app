package com.medicalreportapp.testcatalog;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface TestCatalogRepository extends JpaRepository<TestCatalogEntry, UUID> {

    List<TestCatalogEntry> findAllByOrderByDisplayNameAsc();
}
