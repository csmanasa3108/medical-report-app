package com.medicalreportapp.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "audit_events")
class AuditEvent {

    @Id
    private UUID id;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Column(name = "actor_role", length = 50)
    private String actorRole;

    @Column(name = "patient_user_id")
    private UUID patientUserId;

    @Column(name = "action", nullable = false, length = 100)
    private String action;

    @Column(name = "resource_type", length = 100)
    private String resourceType;

    @Column(name = "resource_id")
    private UUID resourceId;

    @Column(name = "details", columnDefinition = "TEXT")
    private String details;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AuditEvent() {
    }

    AuditEvent(
        UUID id,
        UUID actorUserId,
        String actorRole,
        UUID patientUserId,
        String action,
        String resourceType,
        UUID resourceId,
        String details
    ) {
        this.id = id;
        this.actorUserId = actorUserId;
        this.actorRole = actorRole;
        this.patientUserId = patientUserId;
        this.action = action;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.details = details;
    }

    UUID getId() {
        return id;
    }

    UUID getActorUserId() {
        return actorUserId;
    }

    String getActorRole() {
        return actorRole;
    }

    UUID getPatientUserId() {
        return patientUserId;
    }

    String getAction() {
        return action;
    }

    String getResourceType() {
        return resourceType;
    }

    UUID getResourceId() {
        return resourceId;
    }

    String getDetails() {
        return details;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
