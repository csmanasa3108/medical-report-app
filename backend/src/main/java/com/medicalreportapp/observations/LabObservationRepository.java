package com.medicalreportapp.observations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface LabObservationRepository extends JpaRepository<LabObservation, UUID> {

    Optional<LabObservation> findByIdAndPatientUserId(UUID id, UUID patientUserId);

    Optional<LabObservation> findFirstByPatientUserIdAndTestIdAndObservedAtAndNumericValueAndUnitAndSourceReportIdIsNullAndSourceParsedObservationIdIsNullOrderByIdAsc(
        UUID patientUserId,
        UUID testId,
        LocalDate observedAt,
        BigDecimal numericValue,
        String unit
    );

    Optional<LabObservation> findFirstByPatientUserIdAndSourceParsedObservationIdOrderByIdAsc(
        UUID patientUserId,
        UUID sourceParsedObservationId
    );

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
            and report.patient_user_id = observation.patient_user_id
        where observation.patient_user_id = :patientUserId
            and observation.test_id = :testId
        order by observation.observed_at asc
        """, nativeQuery = true)
    List<LabObservationTrendPointProjection> findTrendPointsByPatientUserIdAndTestId(
        @Param("patientUserId") UUID patientUserId,
        @Param("testId") UUID testId
    );
}
