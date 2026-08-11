package com.medicalreportapp.audit;

import com.medicalreportapp.users.AppUser;
import com.medicalreportapp.users.UserAccessService;
import com.medicalreportapp.users.UserContextResolver;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AuditService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_DETAILS_LENGTH = 1_000;

    private final UserContextResolver userContextResolver;
    private final UserAccessService userAccessService;
    private final AuditEventWriter auditEventWriter;
    private final AuditEventRepository auditEventRepository;

    protected AuditService() {
        this.userContextResolver = null;
        this.userAccessService = null;
        this.auditEventWriter = null;
        this.auditEventRepository = null;
    }

    @Autowired
    public AuditService(
        UserContextResolver userContextResolver,
        UserAccessService userAccessService,
        AuditEventWriter auditEventWriter,
        AuditEventRepository auditEventRepository
    ) {
        this.userContextResolver = userContextResolver;
        this.userAccessService = userAccessService;
        this.auditEventWriter = auditEventWriter;
        this.auditEventRepository = auditEventRepository;
    }

    public void record(String action, UUID patientUserId, String resourceType, UUID resourceId, String details) {
        if (auditEventWriter == null) {
            return;
        }

        try {
            AppUser actor = resolveCurrentActor();
            auditEventWriter.write(new AuditEvent(
                UUID.randomUUID(),
                actor == null ? null : actor.getId(),
                actor == null ? null : actor.getRole().name(),
                patientUserId,
                action,
                resourceType,
                resourceId,
                safeDetails(details)
            ));
        } catch (RuntimeException ignored) {
            // Audit logging is best effort and must not break sensitive user workflows.
        }
    }

    public List<AuditEventResponse> findForCurrentUser(UUID requestedPatientId) {
        UUID patientUserId = userAccessService.resolveReadablePatientId(requestedPatientId);
        return auditEventRepository.findByPatientUserIdOrderByCreatedAtDesc(
                patientUserId,
                PageRequest.of(0, DEFAULT_LIMIT)
            )
            .stream()
            .map(AuditEventResponse::from)
            .toList();
    }

    private AppUser resolveCurrentActor() {
        try {
            return userContextResolver.getCurrentUser();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String safeDetails(String details) {
        if (!StringUtils.hasText(details)) {
            return null;
        }
        return details.length() <= MAX_DETAILS_LENGTH ? details : details.substring(0, MAX_DETAILS_LENGTH);
    }
}
