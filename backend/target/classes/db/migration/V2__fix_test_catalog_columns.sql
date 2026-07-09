ALTER TABLE test_catalog
    ADD COLUMN IF NOT EXISTS canonical_name varchar(255),
    ADD COLUMN IF NOT EXISTS display_name varchar(255),
    ADD COLUMN IF NOT EXISTS category varchar(100);

UPDATE test_catalog
SET canonical_name = name
WHERE canonical_name IS NULL;

UPDATE test_catalog
SET display_name = name
WHERE display_name IS NULL;

ALTER TABLE test_catalog
    ALTER COLUMN canonical_name SET NOT NULL,
    ALTER COLUMN display_name SET NOT NULL;
