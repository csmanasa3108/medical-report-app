DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = current_schema()
            AND table_name = 'lab_observations'
            AND column_name = 'value'
    ) THEN
        ALTER TABLE lab_observations ALTER COLUMN "value" DROP NOT NULL;
    END IF;
END $$;
