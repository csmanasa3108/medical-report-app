package com.medicalreportapp.observations;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface LabObservationRepository extends JpaRepository<LabObservation, UUID> {

    Optional<LabObservation> findByIdAndUserId(UUID id, UUID userId);

    @Query(value = """
        select
            observation.observed_at as "observedAt",
            observation.numeric_value as "numericValue",
            observation.unit as unit,
            observation.source_report_id as "reportId",
            report.original_filename as "reportOriginalFilename",
            report.lab_name as "labName",
            report.report_date as "reportDate",
            observation.source_parsed_observation_id as "parsedObservationId"
        from lab_observations observation
        left join reports report
            on report.id = observation.source_report_id
            and report.user_id = observation.user_id
        where observation.user_id = :userId
            and observation.test_id = :testId
        order by observation.observed_at asc
        """, nativeQuery = true)
    List<LabObservationTrendPointProjection> findTrendPointsByUserIdAndTestId(
        @Param("userId") UUID userId,
        @Param("testId") UUID testId
    );
}
