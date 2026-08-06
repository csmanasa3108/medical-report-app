ALTER TABLE parsed_observations
    ADD COLUMN confirmed_observation_id UUID NULL REFERENCES lab_observations(id),
    ADD COLUMN confirmed_at TIMESTAMP WITH TIME ZONE NULL;
