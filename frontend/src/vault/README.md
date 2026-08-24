# Patient Vault Frontend Layer

This module is the first frontend boundary for SoveraHealth's decentralized patient-owned data direction.

Today, most medical data flows directly through backend APIs. The target model moves PHI-bearing data into a patient-owned encrypted vault while the hosted backend acts as a control plane for identity, sharing, public keys, encrypted key envelopes, vault metadata, and metadata-only audit.

## What Belongs Behind This Layer

Future page migrations should read and write these data types through `PatientVaultService` instead of calling backend APIs directly:

- Reports and report document metadata.
- Parsed observation review items.
- Confirmed observations.
- Trend points.
- Vault-local audit events.
- Vault export and import.

The hosted backend should eventually stop storing raw reports, extracted report text, lab values, parsed observations, confirmed observations, and trend points.

## Modes

`VITE_PATIENT_VAULT_MODE` selects the adapter.

- `api`: default. Keeps current app behavior available through an API-backed adapter. Existing pages are not migrated yet, so current routes continue to use the existing API client directly.
- `local`: development prototype using browser `localStorage`.

Future modes may include encrypted local storage, encrypted export/import, and patient-owned cloud vault connectors.

## API Mode

`ApiBackedPatientVaultService` maps available existing backend API responses into vault model types. Some write/export operations intentionally throw a clear unsupported error because the current backend does not expose a direct safe equivalent.

This mode is a transition adapter, not the target patient-owned storage model.

## Local Mode

`LocalPatientVaultService` stores a thin vault snapshot in `localStorage`.

Important limitations:

- It is not encrypted.
- It is not production-ready.
- It must be used only with synthetic/demo data.
- Browser storage can be cleared by the user or browser.
- It is not a backup, sync, or recovery strategy.

Encryption must be added before any real PHI is stored locally.

## Encryption Requirement

The final vault model must encrypt PHI before it leaves the patient's device. The server must never receive or store plaintext raw reports, extracted text, lab values, parsed observations, confirmed observations, or trend points.

The server also must not store patient private keys. Future clinician sharing should use public keys and encrypted key envelopes so the central database stores only metadata and wrapped keys.

## Migration Notes

Recommended migration order:

1. Keep existing pages on current backend APIs.
2. Route read-only trend and parsed observation workflows through `PatientVaultService`.
3. Add encrypted local vault storage.
4. Move report upload and parsing workflows behind the vault service.
5. Add encrypted export/import.
6. Add patient-owned cloud vault connector support.
7. Add clinician key wrapping and scoped sharing.

Until those steps are complete, hosted demos should use synthetic data only.
