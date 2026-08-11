package com.medicalreportapp.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.medicalreportapp.audit.AuditService;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class PatientClinicianAccessServiceTest {

    private static final UUID PATIENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID OTHER_PATIENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000901");
    private static final UUID CLINICIAN_ID = UUID.fromString("00000000-0000-0000-0000-000000000102");
    private static final UUID ACCESS_ID = UUID.fromString("00000000-0000-0000-0000-000000000921");

    @Test
    void patientCanGrantAccessToClinicianByEmail() {
        FakeAppUsers appUsers = new FakeAppUsers();
        AppUser clinician = new AppUser(
            CLINICIAN_ID,
            "clinician.demo@soverahealth.local",
            "Demo Clinician",
            AppUserRole.CLINICIAN
        );
        appUsers.add(clinician);
        FakePatientClinicianAccessRepository accessRepository = new FakePatientClinicianAccessRepository(appUsers);
        RecordingAuditService auditService = new RecordingAuditService();
        PatientClinicianAccessService service = service(PATIENT_ID, appUsers, accessRepository, auditService);

        PatientClinicianAccessResponse response = service.grantClinicianAccess(new GrantClinicianAccessRequest(
            "clinician.demo@soverahealth.local"
        ));

        assertThat(response.clinicianUserId()).isEqualTo(CLINICIAN_ID);
        assertThat(response.clinicianDisplayName()).isEqualTo("Demo Clinician");
        assertThat(response.clinicianEmail()).isEqualTo("clinician.demo@soverahealth.local");
        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(accessRepository.storedAccesses()).hasSize(1);
        assertThat(accessRepository.savedAccesses()).hasSize(1);
        assertThat(auditService.events()).singleElement()
            .satisfies(event -> {
                assertThat(event.action()).isEqualTo("CLINICIAN_ACCESS_GRANTED");
                assertThat(event.patientUserId()).isEqualTo(PATIENT_ID);
                assertThat(event.resourceType()).isEqualTo("PATIENT_CLINICIAN_ACCESS");
                assertThat(event.resourceId()).isEqualTo(response.accessId());
                assertThat(event.details()).contains(CLINICIAN_ID.toString());
            });
    }

    @Test
    void patientCanReactivateExistingAccessWithoutDuplicate() {
        FakeAppUsers appUsers = new FakeAppUsers();
        AppUser clinician = new AppUser(
            CLINICIAN_ID,
            "clinician.demo@soverahealth.local",
            "Demo Clinician",
            AppUserRole.CLINICIAN
        );
        appUsers.add(clinician);
        FakePatientClinicianAccessRepository accessRepository = new FakePatientClinicianAccessRepository(appUsers);
        accessRepository.add(new PatientClinicianAccess(
            ACCESS_ID,
            PATIENT_ID,
            CLINICIAN_ID,
            PatientClinicianAccessStatus.INACTIVE
        ));
        PatientClinicianAccessService service = service(PATIENT_ID, appUsers, accessRepository);

        PatientClinicianAccessResponse response = service.grantClinicianAccess(new GrantClinicianAccessRequest(
            "clinician.demo@soverahealth.local"
        ));

        assertThat(response.accessId()).isEqualTo(ACCESS_ID);
        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(accessRepository.storedAccesses()).hasSize(1);
        assertThat(accessRepository.savedAccesses()).hasSize(1);
    }

    @Test
    void patientCannotGrantAccessToNonClinician() {
        FakeAppUsers appUsers = new FakeAppUsers();
        appUsers.add(new AppUser(
            OTHER_PATIENT_ID,
            "other.patient@soverahealth.local",
            "Other Patient",
            AppUserRole.PATIENT
        ));
        FakePatientClinicianAccessRepository accessRepository = new FakePatientClinicianAccessRepository(appUsers);
        PatientClinicianAccessService service = service(PATIENT_ID, appUsers, accessRepository);

        assertThatThrownBy(() -> service.grantClinicianAccess(new GrantClinicianAccessRequest(
            "other.patient@soverahealth.local"
        )))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(accessRepository.savedAccesses()).isEmpty();
    }

    @Test
    void clinicianCannotGrantThemselvesAccess() {
        FakeAppUsers appUsers = new FakeAppUsers();
        appUsers.add(new AppUser(
            CLINICIAN_ID,
            "clinician.demo@soverahealth.local",
            "Demo Clinician",
            AppUserRole.CLINICIAN
        ));
        FakePatientClinicianAccessRepository accessRepository = new FakePatientClinicianAccessRepository(appUsers);
        PatientClinicianAccessService service = serviceThatRejectsCurrentUser(appUsers, accessRepository);

        assertThatThrownBy(() -> service.grantClinicianAccess(new GrantClinicianAccessRequest(
            "clinician.demo@soverahealth.local"
        )))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
            .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(accessRepository.savedAccesses()).isEmpty();
    }

    @Test
    void patientCanRevokeOwnClinicianAccess() {
        FakeAppUsers appUsers = new FakeAppUsers();
        AppUser clinician = new AppUser(
            CLINICIAN_ID,
            "clinician.demo@soverahealth.local",
            "Demo Clinician",
            AppUserRole.CLINICIAN
        );
        appUsers.add(clinician);
        FakePatientClinicianAccessRepository accessRepository = new FakePatientClinicianAccessRepository(appUsers);
        accessRepository.add(new PatientClinicianAccess(
            ACCESS_ID,
            PATIENT_ID,
            CLINICIAN_ID,
            PatientClinicianAccessStatus.ACTIVE
        ));
        RecordingAuditService auditService = new RecordingAuditService();
        PatientClinicianAccessService service = service(PATIENT_ID, appUsers, accessRepository, auditService);

        PatientClinicianAccessResponse response = service.revokeClinicianAccess(ACCESS_ID);

        assertThat(response.accessId()).isEqualTo(ACCESS_ID);
        assertThat(response.status()).isEqualTo("INACTIVE");
        assertThat(accessRepository.savedAccesses()).singleElement()
            .extracting(PatientClinicianAccess::getStatus)
            .isEqualTo(PatientClinicianAccessStatus.INACTIVE);
        assertThat(auditService.events()).singleElement()
            .satisfies(event -> {
                assertThat(event.action()).isEqualTo("CLINICIAN_ACCESS_REVOKED");
                assertThat(event.patientUserId()).isEqualTo(PATIENT_ID);
                assertThat(event.resourceType()).isEqualTo("PATIENT_CLINICIAN_ACCESS");
                assertThat(event.resourceId()).isEqualTo(ACCESS_ID);
                assertThat(event.details()).contains(CLINICIAN_ID.toString());
            });
    }

    @Test
    void patientCanRevokeAlreadyInactiveClinicianAccess() {
        FakeAppUsers appUsers = new FakeAppUsers();
        AppUser clinician = new AppUser(
            CLINICIAN_ID,
            "clinician.demo@soverahealth.local",
            "Demo Clinician",
            AppUserRole.CLINICIAN
        );
        appUsers.add(clinician);
        FakePatientClinicianAccessRepository accessRepository = new FakePatientClinicianAccessRepository(appUsers);
        accessRepository.add(new PatientClinicianAccess(
            ACCESS_ID,
            PATIENT_ID,
            CLINICIAN_ID,
            PatientClinicianAccessStatus.INACTIVE
        ));
        PatientClinicianAccessService service = service(PATIENT_ID, appUsers, accessRepository);

        PatientClinicianAccessResponse response = service.revokeClinicianAccess(ACCESS_ID);

        assertThat(response.accessId()).isEqualTo(ACCESS_ID);
        assertThat(response.status()).isEqualTo("INACTIVE");
        assertThat(accessRepository.savedAccesses()).isEmpty();
    }

    @Test
    void patientCannotRevokeAnotherPatientsClinicianAccess() {
        FakeAppUsers appUsers = new FakeAppUsers();
        appUsers.add(new AppUser(
            CLINICIAN_ID,
            "clinician.demo@soverahealth.local",
            "Demo Clinician",
            AppUserRole.CLINICIAN
        ));
        FakePatientClinicianAccessRepository accessRepository = new FakePatientClinicianAccessRepository(appUsers);
        accessRepository.add(new PatientClinicianAccess(
            ACCESS_ID,
            OTHER_PATIENT_ID,
            CLINICIAN_ID,
            PatientClinicianAccessStatus.ACTIVE
        ));
        PatientClinicianAccessService service = service(PATIENT_ID, appUsers, accessRepository);

        assertThatThrownBy(() -> service.revokeClinicianAccess(ACCESS_ID))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(accessRepository.savedAccesses()).isEmpty();
    }

    @Test
    void patientCanListCurrentAndPreviousClinicianAccess() {
        FakeAppUsers appUsers = new FakeAppUsers();
        appUsers.add(new AppUser(
            CLINICIAN_ID,
            "clinician.demo@soverahealth.local",
            "Demo Clinician",
            AppUserRole.CLINICIAN
        ));
        UUID revokedClinicianId = UUID.fromString("00000000-0000-0000-0000-000000000902");
        appUsers.add(new AppUser(
            revokedClinicianId,
            "former.clinician@soverahealth.local",
            "Former Clinician",
            AppUserRole.CLINICIAN
        ));
        FakePatientClinicianAccessRepository accessRepository = new FakePatientClinicianAccessRepository(appUsers);
        accessRepository.add(new PatientClinicianAccess(
            ACCESS_ID,
            PATIENT_ID,
            CLINICIAN_ID,
            PatientClinicianAccessStatus.ACTIVE
        ));
        accessRepository.add(new PatientClinicianAccess(
            UUID.fromString("00000000-0000-0000-0000-000000000922"),
            PATIENT_ID,
            revokedClinicianId,
            PatientClinicianAccessStatus.INACTIVE
        ));
        PatientClinicianAccessService service = service(PATIENT_ID, appUsers, accessRepository);

        List<PatientClinicianAccessResponse> responses = service.findClinicianAccessForCurrentPatient();

        assertThat(responses)
            .extracting(PatientClinicianAccessResponse::status)
            .containsExactly("ACTIVE", "INACTIVE");
    }

    private static PatientClinicianAccessService service(
        UUID currentPatientId,
        FakeAppUsers appUsers,
        FakePatientClinicianAccessRepository accessRepository
    ) {
        return new PatientClinicianAccessService(
            new TestUserAccessService(currentPatientId),
            appUsers.repository(),
            accessRepository.repository(),
            new RecordingAuditService()
        );
    }

    private static PatientClinicianAccessService service(
        UUID currentPatientId,
        FakeAppUsers appUsers,
        FakePatientClinicianAccessRepository accessRepository,
        AuditService auditService
    ) {
        return new PatientClinicianAccessService(
            new TestUserAccessService(currentPatientId),
            appUsers.repository(),
            accessRepository.repository(),
            auditService
        );
    }

    private static PatientClinicianAccessService serviceThatRejectsCurrentUser(
        FakeAppUsers appUsers,
        FakePatientClinicianAccessRepository accessRepository
    ) {
        return new PatientClinicianAccessService(
            new RejectingUserAccessService(),
            appUsers.repository(),
            accessRepository.repository(),
            new RecordingAuditService()
        );
    }

    private static final class RecordingAuditService extends AuditService {

        private final List<RecordedAuditEvent> events = new ArrayList<>();

        @Override
        public void record(String action, UUID patientUserId, String resourceType, UUID resourceId, String details) {
            events.add(new RecordedAuditEvent(action, patientUserId, resourceType, resourceId, details));
        }

        List<RecordedAuditEvent> events() {
            return events;
        }
    }

    private record RecordedAuditEvent(
        String action,
        UUID patientUserId,
        String resourceType,
        UUID resourceId,
        String details
    ) {
    }

    private static final class TestUserAccessService extends UserAccessService {

        private final UUID currentPatientId;

        private TestUserAccessService(UUID currentPatientId) {
            super(null, null);
            this.currentPatientId = currentPatientId;
        }

        @Override
        public UUID requireCurrentUserCanWritePatientData() {
            return currentPatientId;
        }
    }

    private static final class RejectingUserAccessService extends UserAccessService {

        private RejectingUserAccessService() {
            super(null, null);
        }

        @Override
        public UUID requireCurrentUserCanWritePatientData() {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only patients can modify patient data");
        }
    }

    private static final class FakeAppUsers {

        private final Map<UUID, AppUser> usersById = new LinkedHashMap<>();

        void add(AppUser user) {
            usersById.put(user.getId(), user);
        }

        AppUserRepository repository() {
            return createRepository(AppUserRepository.class, (methodName, args) -> {
                if ("findByEmail".equals(methodName)) {
                    String email = (String) args[0];
                    return usersById.values().stream()
                        .filter(user -> user.getEmail().equals(email))
                        .findFirst();
                }
                if ("findById".equals(methodName)) {
                    return Optional.ofNullable(usersById.get((UUID) args[0]));
                }

                throw new AssertionError("Unexpected app user repository call: " + methodName);
            });
        }
    }

    private static final class FakePatientClinicianAccessRepository {

        private final FakeAppUsers appUsers;
        private final Map<UUID, PatientClinicianAccess> accessesById = new LinkedHashMap<>();
        private final List<PatientClinicianAccess> savedAccesses = new ArrayList<>();

        private FakePatientClinicianAccessRepository(FakeAppUsers appUsers) {
            this.appUsers = appUsers;
        }

        void add(PatientClinicianAccess access) {
            accessesById.put(access.getId(), access);
        }

        List<PatientClinicianAccess> storedAccesses() {
            return new ArrayList<>(accessesById.values());
        }

        List<PatientClinicianAccess> savedAccesses() {
            return savedAccesses;
        }

        PatientClinicianAccessRepository repository() {
            return createRepository(PatientClinicianAccessRepository.class, (methodName, args) -> {
                if ("findFirstByPatientUserIdAndClinicianUserIdOrderByCreatedAtAsc".equals(methodName)) {
                    UUID patientUserId = (UUID) args[0];
                    UUID clinicianUserId = (UUID) args[1];
                    return accessesById.values().stream()
                        .filter(access -> access.getPatientUserId().equals(patientUserId))
                        .filter(access -> access.getClinicianUserId().equals(clinicianUserId))
                        .findFirst();
                }
                if ("findByIdAndPatientUserId".equals(methodName)) {
                    UUID accessId = (UUID) args[0];
                    UUID patientUserId = (UUID) args[1];
                    return Optional.ofNullable(accessesById.get(accessId))
                        .filter(access -> access.getPatientUserId().equals(patientUserId));
                }
                if ("saveAndFlush".equals(methodName)) {
                    PatientClinicianAccess access = (PatientClinicianAccess) args[0];
                    accessesById.put(access.getId(), access);
                    savedAccesses.add(access);
                    return access;
                }
                if ("findClinicianAccessForPatient".equals(methodName)) {
                    return findClinicianAccessForPatient((UUID) args[0]);
                }

                throw new AssertionError("Unexpected access repository call: " + methodName);
            });
        }

        private List<PatientClinicianAccessProjection> findClinicianAccessForPatient(UUID patientUserId) {
            return accessesById.values().stream()
                .filter(access -> access.getPatientUserId().equals(patientUserId))
                .<PatientClinicianAccessProjection>map(access -> new TestPatientClinicianAccessProjection(
                    access,
                    appUsers.usersById.get(access.getClinicianUserId())
                ))
                .sorted(Comparator.comparing(PatientClinicianAccessProjection::getStatus))
                .toList();
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T createRepository(Class<T> repositoryType, RepositoryCallHandler callHandler) {
        return (T) Proxy.newProxyInstance(
            repositoryType.getClassLoader(),
            new Class<?>[] { repositoryType },
            (proxy, method, args) -> {
                if (method.getDeclaringClass() == Object.class) {
                    return switch (method.getName()) {
                        case "equals" -> proxy == args[0];
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "toString" -> repositoryType.getSimpleName() + " test proxy";
                        default -> throw new AssertionError("Unexpected object method: " + method.getName());
                    };
                }

                return callHandler.handle(method.getName(), args);
            }
        );
    }

    @FunctionalInterface
    private interface RepositoryCallHandler {
        Object handle(String methodName, Object[] args);
    }

    private record TestPatientClinicianAccessProjection(
        UUID accessId,
        UUID clinicianUserId,
        String clinicianDisplayName,
        String clinicianEmail,
        String status,
        Instant createdAt
    ) implements PatientClinicianAccessProjection {

        TestPatientClinicianAccessProjection(PatientClinicianAccess access, AppUser clinician) {
            this(
                access.getId(),
                clinician.getId(),
                clinician.getDisplayName(),
                clinician.getEmail(),
                access.getStatus().name(),
                access.getCreatedAt()
            );
        }

        @Override
        public UUID getAccessId() {
            return accessId;
        }

        @Override
        public UUID getClinicianUserId() {
            return clinicianUserId;
        }

        @Override
        public String getClinicianDisplayName() {
            return clinicianDisplayName;
        }

        @Override
        public String getClinicianEmail() {
            return clinicianEmail;
        }

        @Override
        public String getStatus() {
            return status;
        }

        @Override
        public Instant getCreatedAt() {
            return createdAt;
        }
    }
}
