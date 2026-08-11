package com.medicalreportapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.medicalreportapp.users.UserContextResolver;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class AccessControlRegressionTest {

    private static final UUID DEMO_PATIENT_APP_USER_ID = UserContextResolver.DEMO_PATIENT_USER_ID;
    private static final UUID DEMO_CLINICIAN_APP_USER_ID = UserContextResolver.DEMO_CLINICIAN_USER_ID;
    private static final UUID UNASSIGNED_PATIENT_APP_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000901");
    private static final UUID DEMO_PATIENT_LEGACY_REPORT_OWNER_USER_ID = DEMO_PATIENT_APP_USER_ID;
    private static final UUID UNASSIGNED_PATIENT_LEGACY_REPORT_OWNER_USER_ID = UNASSIGNED_PATIENT_APP_USER_ID;
    private static final UUID TEST_ID = UUID.fromString("00000000-0000-0000-0000-000000000911");
    private static final UUID DEMO_PATIENT_REPORT_ID = UUID.fromString("00000000-0000-0000-0000-000000000931");
    private static final UUID UNASSIGNED_PATIENT_REPORT_ID = UUID.fromString("00000000-0000-0000-0000-000000000932");
    private static final UUID DEMO_PATIENT_OBSERVATION_ID = UUID.fromString("00000000-0000-0000-0000-000000000941");
    private static final UUID UNASSIGNED_PATIENT_OBSERVATION_ID = UUID.fromString("00000000-0000-0000-0000-000000000942");
    private static final UUID DEMO_PATIENT_AUDIT_EVENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000951");
    private static final UUID UNASSIGNED_PATIENT_AUDIT_EVENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000952");
    private static final UUID DEMO_PATIENT_PARSED_OBSERVATION_ID = UUID.fromString("00000000-0000-0000-0000-000000000961");
    private static final UUID UNASSIGNED_PATIENT_PARSED_OBSERVATION_ID = UUID.fromString("00000000-0000-0000-0000-000000000962");
    private static final UUID DEMO_PATIENT_CONFIRMED_PARSED_OBSERVATION_ID = UUID.fromString("00000000-0000-0000-0000-000000000963");

    private static final String DEMO_PATIENT_REPORT_FILENAME = "access-control-demo-patient.pdf";
    private static final String UNASSIGNED_PATIENT_REPORT_FILENAME = "access-control-unassigned-patient.pdf";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID activeAccessId;

    @BeforeEach
    void setUp() {
        cleanTestRows();
        seedTestRows();
    }

    @AfterEach
    void tearDown() {
        cleanTestRows();
    }

    @Test
    void demoPatientSeesOnlyOwnReportsAndTrendAndCannotListClinicianPatients() throws Exception {
        mockMvc.perform(get("/api/reports").header("X-User-Id", DEMO_PATIENT_APP_USER_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].originalFilename", hasItem(DEMO_PATIENT_REPORT_FILENAME)))
            .andExpect(jsonPath("$[*].originalFilename", not(hasItem(UNASSIGNED_PATIENT_REPORT_FILENAME))));

        mockMvc.perform(get("/api/tests").header("X-User-Id", DEMO_PATIENT_APP_USER_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].id", hasItem(TEST_ID.toString())));

        mockMvc.perform(get("/api/analytics/tests/{testId}/trend", TEST_ID).header("X-User-Id", DEMO_PATIENT_APP_USER_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.points[*].observedAt", hasItem("2026-01-10")))
            .andExpect(jsonPath("$.points[*].observedAt", not(hasItem("2026-01-20"))));

        mockMvc.perform(get("/api/clinician/patients").header("X-User-Id", DEMO_PATIENT_APP_USER_ID))
            .andExpect(status().isForbidden());
    }

    @Test
    void demoClinicianSeesAssignedPatientDataOnly() throws Exception {
        mockMvc.perform(get("/api/clinician/patients").header("X-User-Id", DEMO_CLINICIAN_APP_USER_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].patientId", hasItem(DEMO_PATIENT_APP_USER_ID.toString())))
            .andExpect(jsonPath("$[*].patientId", not(hasItem(UNASSIGNED_PATIENT_APP_USER_ID.toString()))));

        mockMvc.perform(get("/api/reports")
                .header("X-User-Id", DEMO_CLINICIAN_APP_USER_ID)
                .param("patientId", DEMO_PATIENT_APP_USER_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].originalFilename", hasItem(DEMO_PATIENT_REPORT_FILENAME)))
            .andExpect(jsonPath("$[*].originalFilename", not(hasItem(UNASSIGNED_PATIENT_REPORT_FILENAME))));

        mockMvc.perform(get("/api/analytics/tests/{testId}/trend", TEST_ID)
                .header("X-User-Id", DEMO_CLINICIAN_APP_USER_ID)
                .param("patientId", DEMO_PATIENT_APP_USER_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.points[*].observedAt", hasItem("2026-01-10")))
            .andExpect(jsonPath("$.points[*].observedAt", not(hasItem("2026-01-20"))));

        mockMvc.perform(get("/api/reports")
                .header("X-User-Id", DEMO_CLINICIAN_APP_USER_ID)
                .param("patientId", UNASSIGNED_PATIENT_APP_USER_ID.toString()))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/analytics/tests/{testId}/trend", TEST_ID)
                .header("X-User-Id", DEMO_CLINICIAN_APP_USER_ID)
                .param("patientId", UNASSIGNED_PATIENT_APP_USER_ID.toString()))
            .andExpect(status().isForbidden());
    }

    @Test
    void revokingClinicianAccessMarksAccessInactiveAndRemovesAssignedPatient() throws Exception {
        mockMvc.perform(patch("/api/patient/clinician-access/{accessId}/revoke", activeAccessId)
                .header("X-User-Id", DEMO_PATIENT_APP_USER_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessId").value(activeAccessId.toString()))
            .andExpect(jsonPath("$.status").value("INACTIVE"));

        String accessStatus = jdbcTemplate.queryForObject(
            "select status from patient_clinician_access where id = ?",
            String.class,
            activeAccessId
        );
        assertThat(accessStatus).isEqualTo("INACTIVE");

        mockMvc.perform(patch("/api/patient/clinician-access/{accessId}/revoke", activeAccessId)
                .header("X-User-Id", DEMO_PATIENT_APP_USER_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessId").value(activeAccessId.toString()))
            .andExpect(jsonPath("$.status").value("INACTIVE"));

        mockMvc.perform(get("/api/clinician/patients").header("X-User-Id", DEMO_CLINICIAN_APP_USER_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].patientId", not(hasItem(DEMO_PATIENT_APP_USER_ID.toString()))));
    }

    @Test
    void otherPatientCannotRevokeDemoPatientsClinicianAccess() throws Exception {
        mockMvc.perform(patch("/api/patient/clinician-access/{accessId}/revoke", activeAccessId)
                .header("X-User-Id", UNASSIGNED_PATIENT_APP_USER_ID))
            .andExpect(status().isNotFound());
    }

    @Test
    void clinicianCannotRevokeThroughPatientEndpoint() throws Exception {
        mockMvc.perform(patch("/api/patient/clinician-access/{accessId}/revoke", activeAccessId)
                .header("X-User-Id", DEMO_CLINICIAN_APP_USER_ID))
            .andExpect(status().isForbidden());
    }

    @Test
    void patientSeesOnlyOwnAuditEvents() throws Exception {
        mockMvc.perform(get("/api/audit-events").header("X-User-Id", DEMO_PATIENT_APP_USER_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].id", hasItem(DEMO_PATIENT_AUDIT_EVENT_ID.toString())))
            .andExpect(jsonPath("$[*].id", not(hasItem(UNASSIGNED_PATIENT_AUDIT_EVENT_ID.toString()))))
            .andExpect(jsonPath("$[*].details", not(hasItem(containsString("raw extracted report text")))))
            .andExpect(jsonPath("$[*].details", not(hasItem(containsString("12.8000")))));
    }

    @Test
    void patientCannotReadAnotherPatientsAuditEvents() throws Exception {
        mockMvc.perform(get("/api/audit-events")
                .header("X-User-Id", DEMO_PATIENT_APP_USER_ID)
                .param("patientId", UNASSIGNED_PATIENT_APP_USER_ID.toString()))
            .andExpect(status().isForbidden());
    }

    @Test
    void clinicianCanReadAssignedPatientAuditEventsOnlyWithPatientId() throws Exception {
        mockMvc.perform(get("/api/audit-events").header("X-User-Id", DEMO_CLINICIAN_APP_USER_ID))
            .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/audit-events")
                .header("X-User-Id", DEMO_CLINICIAN_APP_USER_ID)
                .param("patientId", DEMO_PATIENT_APP_USER_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].id", hasItem(DEMO_PATIENT_AUDIT_EVENT_ID.toString())))
            .andExpect(jsonPath("$[*].id", not(hasItem(UNASSIGNED_PATIENT_AUDIT_EVENT_ID.toString()))));
    }

    @Test
    void clinicianCannotReadUnassignedPatientAuditEvents() throws Exception {
        mockMvc.perform(get("/api/audit-events")
                .header("X-User-Id", DEMO_CLINICIAN_APP_USER_ID)
                .param("patientId", UNASSIGNED_PATIENT_APP_USER_ID.toString()))
            .andExpect(status().isForbidden());
    }

    @Test
    void patientSeesOnlyOwnNeedsReviewParsedObservations() throws Exception {
        mockMvc.perform(get("/api/review/parsed-observations").header("X-User-Id", DEMO_PATIENT_APP_USER_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].parsedObservationId", hasItem(DEMO_PATIENT_PARSED_OBSERVATION_ID.toString())))
            .andExpect(jsonPath("$[*].parsedObservationId", not(hasItem(UNASSIGNED_PATIENT_PARSED_OBSERVATION_ID.toString()))))
            .andExpect(jsonPath("$[*].parsedObservationId", not(hasItem(DEMO_PATIENT_CONFIRMED_PARSED_OBSERVATION_ID.toString()))));
    }

    @Test
    void patientCannotListAnotherPatientsReviewQueue() throws Exception {
        mockMvc.perform(get("/api/review/parsed-observations")
                .header("X-User-Id", DEMO_PATIENT_APP_USER_ID)
                .param("patientId", UNASSIGNED_PATIENT_APP_USER_ID.toString()))
            .andExpect(status().isForbidden());
    }

    @Test
    void clinicianCanListAssignedPatientReviewQueueOnlyWithPatientId() throws Exception {
        mockMvc.perform(get("/api/review/parsed-observations").header("X-User-Id", DEMO_CLINICIAN_APP_USER_ID))
            .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/review/parsed-observations")
                .header("X-User-Id", DEMO_CLINICIAN_APP_USER_ID)
                .param("patientId", DEMO_PATIENT_APP_USER_ID.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].parsedObservationId", hasItem(DEMO_PATIENT_PARSED_OBSERVATION_ID.toString())))
            .andExpect(jsonPath("$[*].parsedObservationId", not(hasItem(UNASSIGNED_PATIENT_PARSED_OBSERVATION_ID.toString()))));
    }

    @Test
    void clinicianCannotListUnassignedPatientReviewQueue() throws Exception {
        mockMvc.perform(get("/api/review/parsed-observations")
                .header("X-User-Id", DEMO_CLINICIAN_APP_USER_ID)
                .param("patientId", UNASSIGNED_PATIENT_APP_USER_ID.toString()))
            .andExpect(status().isForbidden());
    }

    @Test
    void patientCanRejectOwnNeedsReviewParsedObservationAndAuditMetadataOnly() throws Exception {
        mockMvc.perform(post("/api/parsed-observations/{parsedObservationId}/reject", DEMO_PATIENT_PARSED_OBSERVATION_ID)
                .header("X-User-Id", DEMO_PATIENT_APP_USER_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.parsedObservationId").value(DEMO_PATIENT_PARSED_OBSERVATION_ID.toString()))
            .andExpect(jsonPath("$.status").value("REJECTED"));

        String parsedObservationStatus = jdbcTemplate.queryForObject(
            "select status from parsed_observations where id = ?",
            String.class,
            DEMO_PATIENT_PARSED_OBSERVATION_ID
        );
        assertThat(parsedObservationStatus).isEqualTo("REJECTED");

        Integer auditEventCount = jdbcTemplate.queryForObject(
            "select count(*) from audit_events where action = ? and resource_id = ?",
            Integer.class,
            "PARSED_OBSERVATION_REJECTED",
            DEMO_PATIENT_PARSED_OBSERVATION_ID
        );
        assertThat(auditEventCount).isEqualTo(1);

        String auditDetails = jdbcTemplate.queryForObject(
            "select details from audit_events where action = ? and resource_id = ?",
            String.class,
            "PARSED_OBSERVATION_REJECTED",
            DEMO_PATIENT_PARSED_OBSERVATION_ID
        );
        assertThat(auditDetails).contains(DEMO_PATIENT_REPORT_ID.toString());
        assertThat(auditDetails).doesNotContain("101.0000", "Access Control Regression Test", "mg/dL");
    }

    @Test
    void rejectingConfirmedParsedObservationReturnsConflict() throws Exception {
        mockMvc.perform(post("/api/parsed-observations/{parsedObservationId}/reject", DEMO_PATIENT_CONFIRMED_PARSED_OBSERVATION_ID)
                .header("X-User-Id", DEMO_PATIENT_APP_USER_ID))
            .andExpect(status().isConflict());
    }

    @Test
    void clinicianCannotRejectThroughParsedObservationEndpoint() throws Exception {
        mockMvc.perform(post("/api/parsed-observations/{parsedObservationId}/reject", DEMO_PATIENT_PARSED_OBSERVATION_ID)
                .header("X-User-Id", DEMO_CLINICIAN_APP_USER_ID))
            .andExpect(status().isForbidden());
    }

    @Test
    void clinicianPatientScopedEndpointsRequirePatientId() throws Exception {
        mockMvc.perform(get("/api/reports").header("X-User-Id", DEMO_CLINICIAN_APP_USER_ID))
            .andExpect(status().isBadRequest())
            .andExpect(result -> assertThat(result.getResolvedException())
                .hasMessageContaining("patientId is required for clinician access"));

        mockMvc.perform(get("/api/analytics/tests/{testId}/trend", TEST_ID).header("X-User-Id", DEMO_CLINICIAN_APP_USER_ID))
            .andExpect(status().isBadRequest())
            .andExpect(result -> assertThat(result.getResolvedException())
                .hasMessageContaining("patientId is required for clinician access"));
    }

    @Test
    void missingUserHeaderFallsBackToDemoPatient() throws Exception {
        mockMvc.perform(get("/api/reports"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].originalFilename", hasItem(DEMO_PATIENT_REPORT_FILENAME)))
            .andExpect(jsonPath("$[*].originalFilename", not(hasItem(UNASSIGNED_PATIENT_REPORT_FILENAME))));

        mockMvc.perform(get("/api/analytics/tests/{testId}/trend", TEST_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.points[*].observedAt", hasItem("2026-01-10")))
            .andExpect(jsonPath("$.points[*].observedAt", not(hasItem("2026-01-20"))));
    }

    private void seedTestRows() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        Timestamp createdAt = Timestamp.from(now);

        jdbcTemplate.update("""
            insert into app_users (id, email, display_name, role, created_at)
            values (?, ?, ?, ?, ?)
            on conflict (id) do nothing
            """,
            DEMO_PATIENT_APP_USER_ID,
            "patient.demo@soverahealth.local",
            "Demo Patient",
            "PATIENT",
            createdAt
        );
        jdbcTemplate.update("""
            insert into app_users (id, email, display_name, role, created_at)
            values (?, ?, ?, ?, ?)
            on conflict (id) do nothing
            """,
            DEMO_CLINICIAN_APP_USER_ID,
            "clinician.demo@soverahealth.local",
            "Demo Clinician",
            "CLINICIAN",
            createdAt
        );
        jdbcTemplate.update("""
            insert into app_users (id, email, display_name, role, created_at)
            values (?, ?, ?, ?, ?)
            on conflict (id) do update set
                email = excluded.email,
                display_name = excluded.display_name,
                role = excluded.role
            """,
            UNASSIGNED_PATIENT_APP_USER_ID,
            "access-control-unassigned-patient@example.local",
            "Access Control Unassigned Patient",
            "PATIENT",
            createdAt
        );
        seedLegacyUser(
            DEMO_PATIENT_LEGACY_REPORT_OWNER_USER_ID,
            "access-control-demo-patient-legacy@example.local",
            "Access Control Demo Patient",
            createdAt
        );
        seedLegacyUser(
            UNASSIGNED_PATIENT_LEGACY_REPORT_OWNER_USER_ID,
            "access-control-unassigned-patient-legacy@example.local",
            "Access Control Unassigned Patient",
            createdAt
        );
        jdbcTemplate.update("""
            insert into test_catalog (id, name, canonical_name, display_name, default_unit, reference_range, category)
            values (?, ?, ?, ?, ?, ?, ?)
            on conflict (id) do update set
                name = excluded.name,
                canonical_name = excluded.canonical_name,
                display_name = excluded.display_name,
                default_unit = excluded.default_unit,
                reference_range = excluded.reference_range,
                category = excluded.category
            """,
            TEST_ID,
            "Access Control Regression Test",
            "access_control_regression_test",
            "Access Control Regression Test",
            "mg/dL",
            null,
            "Regression"
        );
        seedActiveClinicianAccess(createdAt);
        insertReport(
            DEMO_PATIENT_REPORT_ID,
            DEMO_PATIENT_LEGACY_REPORT_OWNER_USER_ID,
            DEMO_PATIENT_APP_USER_ID,
            DEMO_PATIENT_REPORT_FILENAME,
            LocalDate.parse("2026-01-10"),
            createdAt
        );
        insertReport(
            UNASSIGNED_PATIENT_REPORT_ID,
            UNASSIGNED_PATIENT_LEGACY_REPORT_OWNER_USER_ID,
            UNASSIGNED_PATIENT_APP_USER_ID,
            UNASSIGNED_PATIENT_REPORT_FILENAME,
            LocalDate.parse("2026-01-20"),
            createdAt
        );
        insertObservation(DEMO_PATIENT_OBSERVATION_ID, DEMO_PATIENT_APP_USER_ID, LocalDate.parse("2026-01-10"), new BigDecimal("101.0000"));
        insertObservation(UNASSIGNED_PATIENT_OBSERVATION_ID, UNASSIGNED_PATIENT_APP_USER_ID, LocalDate.parse("2026-01-20"), new BigDecimal("202.0000"));
        insertParsedObservation(
            DEMO_PATIENT_PARSED_OBSERVATION_ID,
            DEMO_PATIENT_REPORT_ID,
            LocalDate.parse("2026-01-10"),
            new BigDecimal("101.0000"),
            "NEEDS_REVIEW",
            Timestamp.from(Instant.parse("2026-01-02T00:00:00Z"))
        );
        insertParsedObservation(
            UNASSIGNED_PATIENT_PARSED_OBSERVATION_ID,
            UNASSIGNED_PATIENT_REPORT_ID,
            LocalDate.parse("2026-01-20"),
            new BigDecimal("202.0000"),
            "NEEDS_REVIEW",
            Timestamp.from(Instant.parse("2026-01-03T00:00:00Z"))
        );
        insertParsedObservation(
            DEMO_PATIENT_CONFIRMED_PARSED_OBSERVATION_ID,
            DEMO_PATIENT_REPORT_ID,
            LocalDate.parse("2026-01-11"),
            new BigDecimal("111.0000"),
            "CONFIRMED",
            Timestamp.from(Instant.parse("2026-01-04T00:00:00Z"))
        );
        insertAuditEvent(
            DEMO_PATIENT_AUDIT_EVENT_ID,
            DEMO_PATIENT_APP_USER_ID,
            DEMO_PATIENT_REPORT_ID,
            "REPORT_UPLOADED",
            "{\"contentType\":\"application/pdf\",\"fileSizeBytes\":128}"
        );
        insertAuditEvent(
            UNASSIGNED_PATIENT_AUDIT_EVENT_ID,
            UNASSIGNED_PATIENT_APP_USER_ID,
            UNASSIGNED_PATIENT_REPORT_ID,
            "REPORT_UPLOADED",
            "{\"contentType\":\"application/pdf\",\"fileSizeBytes\":256}"
        );
    }

    private void seedActiveClinicianAccess(Timestamp createdAt) {
        activeAccessId = jdbcTemplate.query("""
            select id
            from patient_clinician_access
            where patient_user_id = ?
                and clinician_user_id = ?
            order by created_at asc, id asc
            limit 1
            """,
            (resultSet, rowNumber) -> resultSet.getObject("id", UUID.class),
            DEMO_PATIENT_APP_USER_ID,
            DEMO_CLINICIAN_APP_USER_ID
        ).stream().findFirst().orElseGet(UUID::randomUUID);

        jdbcTemplate.update("""
            update patient_clinician_access
            set status = ?
            where patient_user_id = ?
                and clinician_user_id = ?
                and id <> ?
            """,
            "INACTIVE",
            DEMO_PATIENT_APP_USER_ID,
            DEMO_CLINICIAN_APP_USER_ID,
            activeAccessId
        );

        int updatedRows = jdbcTemplate.update("""
            update patient_clinician_access
            set status = ?
            where id = ?
            """,
            "ACTIVE",
            activeAccessId
        );
        if (updatedRows > 0) {
            return;
        }

        jdbcTemplate.update("""
            insert into patient_clinician_access (id, patient_user_id, clinician_user_id, status, created_at)
            values (?, ?, ?, ?, ?)
            """,
            activeAccessId,
            DEMO_PATIENT_APP_USER_ID,
            DEMO_CLINICIAN_APP_USER_ID,
            "ACTIVE",
            createdAt
        );
    }

    private void seedLegacyUser(UUID userId, String email, String displayName, Timestamp createdAt) {
        jdbcTemplate.update("""
            insert into users (
                id,
                email,
                display_name,
                created_at
            )
            values (?, ?, ?, ?)
            on conflict (id) do update set
                email = excluded.email,
                display_name = excluded.display_name
            """,
            userId,
            email,
            displayName,
            createdAt
        );
    }

    private void insertReport(
        UUID reportId,
        UUID legacyReportOwnerUserId,
        UUID patientAppUserId,
        String filename,
        LocalDate reportDate,
        Timestamp createdAt
    ) {
        jdbcTemplate.update("""
            insert into reports (
                id,
                user_id,
                patient_user_id,
                uploaded_by_user_id,
                original_filename,
                report_date,
                lab_name,
                status,
                created_at
            )
            values (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            reportId,
            legacyReportOwnerUserId,
            patientAppUserId,
            patientAppUserId,
            filename,
            Date.valueOf(reportDate),
            "Access Control Lab",
            "CREATED",
            createdAt
        );
    }

    private void insertObservation(UUID observationId, UUID patientId, LocalDate observedAt, BigDecimal numericValue) {
        jdbcTemplate.update("""
            insert into lab_observations (
                id,
                user_id,
                patient_user_id,
                created_by_user_id,
                test_id,
                observed_at,
                numeric_value,
                unit,
                reference_low,
                reference_high,
                abnormal_flag
            )
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            observationId,
            patientId,
            patientId,
            patientId,
            TEST_ID,
            Date.valueOf(observedAt),
            numericValue,
            "mg/dL",
            new BigDecimal("0.0000"),
            new BigDecimal("999.0000"),
            "NORMAL"
        );
    }

    private void insertParsedObservation(
        UUID parsedObservationId,
        UUID reportId,
        LocalDate observedAt,
        BigDecimal numericValue,
        String status,
        Timestamp createdAt
    ) {
        jdbcTemplate.update("""
            insert into parsed_observations (
                id,
                report_id,
                raw_test_name,
                matched_test_id,
                observed_at,
                raw_value,
                numeric_value,
                unit,
                reference_range,
                status,
                created_at
            )
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            parsedObservationId,
            reportId,
            "Access Control Regression Test",
            TEST_ID,
            Date.valueOf(observedAt),
            numericValue.toPlainString(),
            numericValue,
            "mg/dL",
            "0 - 999",
            status,
            createdAt
        );
    }

    private void insertAuditEvent(UUID auditEventId, UUID patientId, UUID reportId, String action, String details) {
        jdbcTemplate.update("""
            insert into audit_events (
                id,
                actor_user_id,
                actor_role,
                patient_user_id,
                action,
                resource_type,
                resource_id,
                details,
                created_at
            )
            values (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            auditEventId,
            patientId,
            "PATIENT",
            patientId,
            action,
            "REPORT",
            reportId,
            details,
            Timestamp.from(Instant.parse("2026-01-01T00:00:00Z"))
        );
    }

    private void cleanTestRows() {
        jdbcTemplate.update("delete from audit_events where patient_user_id in (?, ?)", DEMO_PATIENT_APP_USER_ID, UNASSIGNED_PATIENT_APP_USER_ID);
        jdbcTemplate.update("delete from parsed_observations where id in (?, ?, ?)", DEMO_PATIENT_PARSED_OBSERVATION_ID, UNASSIGNED_PATIENT_PARSED_OBSERVATION_ID, DEMO_PATIENT_CONFIRMED_PARSED_OBSERVATION_ID);
        jdbcTemplate.update("delete from lab_observations where id in (?, ?)", DEMO_PATIENT_OBSERVATION_ID, UNASSIGNED_PATIENT_OBSERVATION_ID);
        jdbcTemplate.update("delete from reports where id in (?, ?)", DEMO_PATIENT_REPORT_ID, UNASSIGNED_PATIENT_REPORT_ID);
        if (activeAccessId != null) {
            jdbcTemplate.update("update patient_clinician_access set status = ? where id = ?", "ACTIVE", activeAccessId);
        }
        jdbcTemplate.update("delete from test_catalog where id = ?", TEST_ID);
        jdbcTemplate.update("delete from app_users where id = ?", UNASSIGNED_PATIENT_APP_USER_ID);
        jdbcTemplate.update("delete from users where id = ?", UNASSIGNED_PATIENT_LEGACY_REPORT_OWNER_USER_ID);
    }
}
