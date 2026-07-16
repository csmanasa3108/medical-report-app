ALTER TABLE reports
    ADD COLUMN extracted_text TEXT,
    ADD COLUMN extraction_status VARCHAR(50),
    ADD COLUMN extracted_at TIMESTAMP WITH TIME ZONE;
