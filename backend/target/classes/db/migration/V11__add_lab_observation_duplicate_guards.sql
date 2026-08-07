do $$
begin
    if not exists (
        select 1
        from lab_observations
        where source_parsed_observation_id is not null
        group by source_parsed_observation_id
        having count(*) > 1
    ) then
        create unique index if not exists idx_lab_observations_unique_source_parsed_observation_id
            on lab_observations(source_parsed_observation_id)
            where source_parsed_observation_id is not null;
    end if;
end $$;
