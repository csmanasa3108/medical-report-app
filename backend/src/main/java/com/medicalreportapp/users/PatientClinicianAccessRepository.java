package com.medicalreportapp.users;

import java.util.List;
import java.util.Optional;
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

    Optional<PatientClinicianAccess> findFirstByPatientUserIdAndClinicianUserIdOrderByCreatedAtAsc(
        UUID patientUserId,
        UUID clinicianUserId
    );

    Optional<PatientClinicianAccess> findByIdAndPatientUserId(UUID id, UUID patientUserId);

    @Query(value = """
        select
            access.id as "accessId",
            clinician.id as "clinicianUserId",
            clinician.display_name as "clinicianDisplayName",
            clinician.email as "clinicianEmail",
            access.status as status,
            access.created_at as "createdAt"
        from patient_clinician_access access
        join app_users clinician
            on clinician.id = access.clinician_user_id
        where access.patient_user_id = :patientUserId
            and clinician.role = :clinicianRole
        order by
            case when access.status = :activeStatus then 0 else 1 end,
            clinician.display_name asc,
            clinician.email asc
        """, nativeQuery = true)
    List<PatientClinicianAccessProjection> findClinicianAccessForPatient(
        @Param("patientUserId") UUID patientUserId,
        @Param("clinicianRole") String clinicianRole,
        @Param("activeStatus") String activeStatus
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
