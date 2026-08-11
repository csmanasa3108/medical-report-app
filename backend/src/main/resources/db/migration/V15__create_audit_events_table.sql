CREATE TABLE audit_events (
    id UUID PRIMARY KEY,
    actor_user_id UUID,
    actor_role VARCHAR(50),
    patient_user_id UUID,
    action VARCHAR(100) NOT NULL,
    resource_type VARCHAR(100),
    resource_id UUID,
    details TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_audit_events_patient_created_at
ON audit_events (patient_user_id, created_at DESC);
