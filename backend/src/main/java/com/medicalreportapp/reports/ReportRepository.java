package com.medicalreportapp.reports;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ReportRepository extends JpaRepository<Report, UUID> {

    List<Report> findByPatientUserIdOrderByCreatedAtDesc(UUID patientUserId);

    Optional<Report> findByIdAndPatientUserId(UUID id, UUID patientUserId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select report from Report report where report.id = :id and report.patientUserId = :patientUserId")
    Optional<Report> findByIdAndPatientUserIdForUpdate(@Param("id") UUID id, @Param("patientUserId") UUID patientUserId);
}
