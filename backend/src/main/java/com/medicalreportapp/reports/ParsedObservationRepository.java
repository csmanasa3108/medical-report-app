package com.medicalreportapp.reports;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ParsedObservationRepository extends JpaRepository<ParsedObservation, UUID> {

    void deleteByReportIdAndStatusNot(UUID reportId, ParsedObservationStatus status);

    List<ParsedObservation> findByReportIdAndStatusOrderByCreatedAtAsc(UUID reportId, ParsedObservationStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select observation from ParsedObservation observation where observation.id = :id")
    Optional<ParsedObservation> findByIdForUpdate(@Param("id") UUID id);

    List<ParsedObservation> findByReportIdOrderByCreatedAtAsc(UUID reportId);
}
