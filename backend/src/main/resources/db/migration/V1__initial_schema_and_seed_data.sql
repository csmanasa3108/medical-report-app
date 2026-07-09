CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    display_name VARCHAR(120) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE test_catalog (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL UNIQUE,
    default_unit VARCHAR(32),
    reference_range VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE lab_observations (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    test_id UUID NOT NULL REFERENCES test_catalog(id),
    observed_at DATE NOT NULL,
    value NUMERIC(12, 4) NOT NULL,
    unit VARCHAR(32),
    reference_range VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_lab_observations_user_test_observed_at
    ON lab_observations(user_id, test_id, observed_at);

INSERT INTO users (id, email, display_name)
VALUES ('00000000-0000-4000-8000-000000000001', 'default.user@example.local', 'Default User');

INSERT INTO test_catalog (id, name, default_unit, reference_range)
VALUES
    ('00000000-0000-4000-8000-000000000101', 'Hemoglobin', 'g/dL', NULL),
    ('00000000-0000-4000-8000-000000000102', 'WBC', '10^3/uL', NULL),
    ('00000000-0000-4000-8000-000000000103', 'Vitamin D', 'ng/mL', NULL),
    ('00000000-0000-4000-8000-000000000104', 'TSH', 'uIU/mL', NULL),
    ('00000000-0000-4000-8000-000000000105', 'LDL', 'mg/dL', NULL),
    ('00000000-0000-4000-8000-000000000106', 'HDL', 'mg/dL', NULL),
    ('00000000-0000-4000-8000-000000000107', 'Glucose', 'mg/dL', NULL);
