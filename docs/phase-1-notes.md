# Phase 1 Notes

## Current scope

Phase 1 is limited to local app foundation, manual lab observation entry, and simple trend analytics. PDF upload, OCR/Textract, AWS services, and authentication are intentionally out of scope.

## Schema compatibility notes

The backend currently maps `lab_observations` through the Phase 1 API fields:

- `id`
- `user_id`
- `test_id`
- `observed_at`
- `numeric_value`
- `unit`
- `reference_low`
- `reference_high`
- `abnormal_flag`

The backend currently maps `test_catalog` through:

- `id`
- `canonical_name`
- `display_name`
- `default_unit`
- `category`

There are Flyway scripts named as compatibility/fix migrations for `test_catalog` and `lab_observations`, including a migration that relaxes a legacy observation value column. Those migration files were not changed during this cleanup. Dropping any legacy columns should be deferred until the current local database shape is inspected directly and it is clear that no existing data, Flyway history, or Hibernate validation depends on them.

## Data handling

Manual observation values are accepted by API request and persisted, but the app should continue to avoid logging medical values. All current observation reads and writes use the default local user id and repository queries scope trend data by `user_id`.
