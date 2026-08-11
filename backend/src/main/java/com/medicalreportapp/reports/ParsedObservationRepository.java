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

    void deleteByReportIdAndStatus(UUID reportId, ParsedObservationStatus status);

    void deleteByReportId(UUID reportId);

    boolean existsByReportId(UUID reportId);

    boolean existsByReportIdAndStatus(UUID reportId, ParsedObservationStatus status);

    List<ParsedObservation> findByReportIdAndStatusOrderByCreatedAtAsc(UUID reportId, ParsedObservationStatus status);

    List<ParsedObservation> findByReportIdAndStatusInOrderByCreatedAtAsc(UUID reportId, List<ParsedObservationStatus> statuses);

    @Query("""
        select parsedObservation.id as parsedObservationId,
            medicalReport.id as reportId,
            medicalReport.originalFilename as reportOriginalFilename,
            medicalReport.labName as labName,
            medicalReport.reportDate as reportDate,
            parsedObservation.matchedTestId as testId,
            coalesce(testCatalog.displayName, parsedObservation.rawTestName) as testName,
            parsedObservation.observedAt as observedAt,
            parsedObservation.rawValue as valueText,
            parsedObservation.numericValue as numericValue,
            parsedObservation.unit as unit,
            parsedObservation.referenceRange as referenceRange,
            parsedObservation.status as status,
            parsedObservation.createdAt as createdAt
        from ParsedObservation parsedObservation
        join Report medicalReport on medicalReport.id = parsedObservation.reportId
        left join TestCatalogEntry testCatalog on testCatalog.id = parsedObservation.matchedTestId
        where medicalReport.patientUserId = :patientUserId
            and parsedObservation.status = :status
        order by parsedObservation.createdAt desc, parsedObservation.id desc
        """)
    List<ParsedObservationReviewProjection> findReviewQueueByPatientUserIdAndStatus(
        @Param("patientUserId") UUID patientUserId,
        @Param("status") ParsedObservationStatus status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select observation from ParsedObservation observation where observation.id = :id")
    Optional<ParsedObservation> findByIdForUpdate(@Param("id") UUID id);

    List<ParsedObservation> findByReportIdOrderByCreatedAtAsc(UUID reportId);
}
