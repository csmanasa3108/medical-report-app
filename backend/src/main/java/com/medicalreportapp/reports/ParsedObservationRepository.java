package com.medicalreportapp.reports;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface ParsedObservationRepository extends JpaRepository<ParsedObservation, UUID> {

    void deleteByReportId(UUID reportId);

    List<ParsedObservation> findByReportIdOrderByCreatedAtAsc(UUID reportId);
}
