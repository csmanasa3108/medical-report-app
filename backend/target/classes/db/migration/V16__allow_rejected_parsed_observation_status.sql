ALTER TABLE parsed_observations
DROP CONSTRAINT IF EXISTS parsed_observations_status_check;

ALTER TABLE parsed_observations
ADD CONSTRAINT parsed_observations_status_check
CHECK (status IN ('NEEDS_REVIEW', 'CONFIRMED', 'REJECTED'));
