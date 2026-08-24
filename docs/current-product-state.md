# Current Product State

SoveraHealth Diagnostic Reports now supports two modes:

## API Mode
- Uses backend APIs and PostgreSQL.
- Supports report upload, parsing, review queue, trends, activity, and care team sharing.
- Intended for synthetic/demo data only while PHI storage is being redesigned.

## Local Vault Mode
- Uses browser-side encrypted local vault prototype.
- Patient can unlock/create a vault with a passphrase.
- Patient can add manual observations.
- Manual observations are encrypted locally.
- Trends can display local manual observations.
- Export/import backup flow exists.
- Local mode is prototype-only and not for real PHI yet.

## Architecture Direction
- Central backend should become a control plane.
- Patient medical data should move to patient-owned encrypted storage.
- Server should not store raw reports, extracted text, parsed observations, confirmed observations, or trend values.
