package com.medicalreportapp.users;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface PatientClinicianAccessRepository extends JpaRepository<PatientClinicianAccess, UUID> {

    boolean existsByPatientUserIdAndClinicianUserIdAndStatus(
        UUID patientUserId,
        UUID clinicianUserId,
        PatientClinicianAccessStatus status
    );

    @Query(value = """
        select
            patient.id as "patientId",
            patient.display_name as "displayName",
            patient.email as email,
            patient.role as role,
            access.status as "accessStatus",
            count(report.id) as "reportCount",
            max(report.report_date) as "latestReportDate"
        from patient_clinician_access access
        join app_users patient
            on patient.id = access.patient_user_id
        left join reports report
            on coalesce(report.patient_user_id, report.user_id) = patient.id
        where access.clinician_user_id = :clinicianUserId
            and access.status = :accessStatus
            and patient.role = :patientRole
        group by
            patient.id,
            patient.display_name,
            patient.email,
            patient.role,
            access.status
        order by patient.display_name asc, patient.email asc
        """, nativeQuery = true)
    List<AssignedPatientProjection> findAssignedPatientsForClinician(
        @Param("clinicianUserId") UUID clinicianUserId,
        @Param("accessStatus") String accessStatus,
        @Param("patientRole") String patientRole
    );
}
