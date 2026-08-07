-- Local/dev helper: inspect duplicate final lab observations.
--
-- This script is read-only. Run it before any cleanup script.
-- Medical report data is sensitive; use only against local or synthetic dev data.
--
-- Duplicate key:
--   user_id, test_id, observed_at, numeric_value, unit, source_type
--
-- source_type is derived from lab_observations source linkage:
--   MANUAL = no source_report_id and no source_parsed_observation_id
--   REPORT = source_report_id or source_parsed_observation_id is present

with observations_with_source as (
    select
        observation.id,
        observation.user_id,
        observation.test_id,
        observation.observed_at,
        observation.numeric_value,
        observation.unit,
        case
            when observation.source_report_id is null
                and observation.source_parsed_observation_id is null
                then 'MANUAL'
            when observation.source_report_id is not null
                or observation.source_parsed_observation_id is not null
                then 'REPORT'
            else 'UNKNOWN'
        end as source_type,
        observation.source_report_id,
        observation.source_parsed_observation_id
    from lab_observations observation
),
duplicate_groups as (
    select
        user_id,
        test_id,
        observed_at,
        numeric_value,
        unit,
        source_type,
        count(*) as duplicate_count,
        array_agg(id order by id) as observation_ids
    from observations_with_source
    group by
        user_id,
        test_id,
        observed_at,
        numeric_value,
        unit,
        source_type
    having count(*) > 1
)
select
    duplicate_groups.user_id,
    duplicate_groups.test_id,
    duplicate_groups.observed_at,
    duplicate_groups.numeric_value,
    duplicate_groups.unit,
    duplicate_groups.source_type,
    duplicate_groups.duplicate_count,
    duplicate_groups.observation_ids
from duplicate_groups
order by
    duplicate_groups.source_type,
    duplicate_groups.user_id,
    duplicate_groups.test_id,
    duplicate_groups.observed_at,
    duplicate_groups.numeric_value,
    duplicate_groups.unit;

-- MANUAL-only duplicate details, useful before running the optional dev cleanup.
with manual_observations as (
    select
        observation.id,
        observation.user_id,
        observation.test_id,
        observation.observed_at,
        observation.numeric_value,
        observation.unit,
        observation.reference_low,
        observation.reference_high,
        observation.abnormal_flag,
        count(*) over (
            partition by
                observation.user_id,
                observation.test_id,
                observation.observed_at,
                observation.numeric_value,
                observation.unit
        ) as duplicate_count
    from lab_observations observation
    where observation.source_report_id is null
        and observation.source_parsed_observation_id is null
)
select
    id,
    user_id,
    test_id,
    observed_at,
    numeric_value,
    unit,
    reference_low,
    reference_high,
    abnormal_flag,
    duplicate_count
from manual_observations
where duplicate_count > 1
order by
    user_id,
    test_id,
    observed_at,
    numeric_value,
    unit,
    id;
