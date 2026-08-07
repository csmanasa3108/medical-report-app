-- DEV ONLY: optional cleanup for duplicate MANUAL lab observations.
--
-- Do not run this against production or real patient data.
-- Do not wire this into application startup.
-- Do not convert this to a Flyway migration.
--
-- Recommended workflow:
--   1. Run backend/scripts/find_duplicate_lab_observations.sql first.
--   2. Back up your local database.
--   3. Run this script only if the duplicate groups are synthetic/manual test data.
--   4. Re-run the find script to verify the result.
--
-- Safety behavior:
--   - Cleans MANUAL duplicates only:
--       source_report_id is null and source_parsed_observation_id is null.
--   - Keeps the oldest row in each duplicate group.
--   - Does not delete any row referenced by parsed_observations.confirmed_observation_id.
--
-- Oldest-row behavior:
--   - If lab_observations.created_at exists in your local schema, it is used.
--   - Otherwise this falls back to ctid as a local-dev-only insertion-order proxy.
--     ctid is not a production-grade ordering mechanism.

begin;

do $$
declare
    has_created_at boolean;
    order_expression text;
    deleted_count integer;
begin
    select exists (
        select 1
        from information_schema.columns
        where table_schema = current_schema()
            and table_name = 'lab_observations'
            and column_name = 'created_at'
    )
    into has_created_at;

    if has_created_at then
        order_expression := 'observation.created_at asc nulls last, observation.id asc';
    else
        raise notice 'lab_observations.created_at not found; using ctid as a local-dev-only fallback.';
        order_expression := 'observation.ctid asc';
    end if;

    execute format($cleanup$
        with ranked_manual_observations as (
            select
                observation.id,
                row_number() over (
                    partition by
                        observation.user_id,
                        observation.test_id,
                        observation.observed_at,
                        observation.numeric_value,
                        observation.unit
                    order by %s
                ) as duplicate_rank
            from lab_observations observation
            where observation.source_report_id is null
                and observation.source_parsed_observation_id is null
        ),
        delete_candidates as (
            select ranked_manual_observations.id
            from ranked_manual_observations
            where ranked_manual_observations.duplicate_rank > 1
                and not exists (
                    select 1
                    from parsed_observations parsed_observation
                    where parsed_observation.confirmed_observation_id = ranked_manual_observations.id
                )
        )
        delete from lab_observations observation
        using delete_candidates
        where observation.id = delete_candidates.id
    $cleanup$, order_expression);

    get diagnostics deleted_count = row_count;
    raise notice 'Deleted % duplicate manual lab observation rows.', deleted_count;
end $$;

-- Inspect the notice and any follow-up duplicate query output before committing.
-- Replace rollback with commit only after confirming this is the intended local cleanup.
rollback;

-- commit;
