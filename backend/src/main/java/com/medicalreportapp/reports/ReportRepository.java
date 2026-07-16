package com.medicalreportapp.reports;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface ReportRepository extends JpaRepository<Report, UUID> {

    List<Report> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<Report> findByIdAndUserId(UUID id, UUID userId);
}
