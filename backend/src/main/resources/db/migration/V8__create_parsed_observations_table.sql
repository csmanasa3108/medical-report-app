CREATE TABLE parsed_observations (
    id UUID PRIMARY KEY,
    report_id UUID NOT NULL REFERENCES reports(id),
    raw_test_name VARCHAR(255) NOT NULL,
    matched_test_id UUID NULL REFERENCES test_catalog(id),
    observed_at DATE NULL,
    raw_value VARCHAR(100),
    numeric_value DECIMAL(12,4),
    unit VARCHAR(50),
    reference_range VARCHAR(255),
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_parsed_observations_report_id ON parsed_observations(report_id);
