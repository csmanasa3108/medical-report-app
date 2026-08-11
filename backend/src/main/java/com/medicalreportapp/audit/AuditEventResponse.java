package com.medicalreportapp.audit;

import java.time.Instant;
import java.util.UUID;

public record AuditEventResponse(
    UUID id,
    UUID actorUserId,
    String actorRole,
    UUID patientUserId,
    String action,
    String resourceType,
    UUID resourceId,
    String details,
    Instant createdAt
) {

    static AuditEventResponse from(AuditEvent auditEvent) {
        return new AuditEventResponse(
            auditEvent.getId(),
            auditEvent.getActorUserId(),
            auditEvent.getActorRole(),
            auditEvent.getPatientUserId(),
            auditEvent.getAction(),
            auditEvent.getResourceType(),
            auditEvent.getResourceId(),
            auditEvent.getDetails(),
            auditEvent.getCreatedAt()
        );
    }
}
