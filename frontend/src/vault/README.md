# Patient Vault Frontend Layer

This module is the first frontend boundary for SoveraHealth's decentralized patient-owned data direction.

Today, most medical data flows directly through backend APIs. The target model moves PHI-bearing data into a patient-owned encrypted vault while the hosted backend acts as a control plane for identity, sharing, public keys, encrypted key envelopes, vault metadata, and metadata-only audit.

## What Belongs Behind This Layer

Future page migrations should read and write these data types through `PatientVaultService` instead of calling backend APIs directly:

- Reports, report document metadata, upload, and delete actions.
- Parsed observation review items, report-level refresh, edit, confirm, and reject actions.
- Confirmed observations.
- Trend tests, trend summaries, and trend points.
- Vault-local audit events.
- Vault export and import.

The hosted backend should eventually stop storing raw reports, extracted report text, lab values, parsed observations, confirmed observations, and trend points.

## Modes

`VITE_PATIENT_VAULT_MODE` selects the adapter.

- `api`: default. Keeps current app behavior available through an API-backed adapter. Report list/detail/upload/delete, parsed observation report detail flows, Review Queue, Trends, individual trend detail pages, Activity/Audit history, and the patient dashboard report summary now use this service in API mode.
- `local`: development prototype using browser `localStorage`.

Future modes may include encrypted local storage, encrypted export/import, and patient-owned cloud vault connectors.

## API Mode

`ApiBackedPatientVaultService` maps available existing backend API responses into vault model types. Report, parsed observation, confirmed observation, trend, and Activity/Audit flows still use the existing backend in this mode, so API mode remains a centralized demo path and can still store report PHI, extracted result metadata, lab values, trend data, and backend audit metadata centrally. Some non-report write/export operations intentionally throw a clear unsupported error because the current backend does not expose a direct safe equivalent.

This mode is a transition adapter, not the target patient-owned storage model.

## Local Encrypted Mode

`LocalPatientVaultService` is a first encrypted local vault prototype for development. It is enabled only when `VITE_PATIENT_VAULT_MODE=local`; `api` remains the default.

When local mode is enabled, the app shows an unlock/create screen before rendering patient data. The user enters a passphrase, and the browser derives a non-extractable AES key with Web Crypto:

- PBKDF2
- SHA-256
- a random stored salt
- AES-GCM for encryption
- a fresh random IV for each encrypted save

The local vault stores one encrypted JSON snapshot with:

- `manifest`
- `reports`
- `parsedObservations`
- `confirmedObservations`
- `auditEvents`

The encrypted vault blob is stored in `localStorage` under a development-only key. The stored blob contains encryption metadata such as salt, IV, KDF parameters, timestamps, and ciphertext. It must not contain readable report metadata, lab values, parsed observations, confirmed observations, audit event details, or plaintext vault JSON.

The app does not store the plaintext passphrase, and the Web Crypto key is created as non-extractable. Decrypted vault data is kept only in memory after unlock and is not available after a page reload until the user unlocks again.

Important limitations:

- It is not production-ready.
- It must be used only with synthetic/demo data.
- `localStorage` is not an ideal production vault storage layer.
- Browser storage can be cleared by the user or browser.
- Losing the passphrase means the vault cannot be recovered.
- Losing the browser/device can mean data loss without an encrypted export backup.
- There is no server-side recovery.
- Upload/PDF parsing still requires API mode for full behavior; local mode stores report metadata only for uploads.

Real PHI must not be stored in this prototype.

Activity/Audit history now reads through `PatientVaultService`. In the final encrypted vault model, detailed patient-local audit events may live in the patient-owned vault, while central backend audit should be limited to metadata/control-plane events such as account, sharing, and key-envelope operations.

## Encryption Requirement

The final vault model must encrypt PHI before it leaves the patient's device. The server must never receive or store plaintext raw reports, extracted text, lab values, parsed observations, confirmed observations, or trend points.

The server also must not store patient private keys. Future clinician sharing should use public keys and encrypted key envelopes so the central database stores only metadata and wrapped keys.

## Migration Notes

Recommended migration order:

1. Keep existing pages on current backend APIs.
2. Move report list/detail/upload/delete and simple report summaries behind `PatientVaultService`. This has started.
3. Move parsed observation report detail flows and Review Queue behind `PatientVaultService`. This has started.
4. Route confirmed-observation and read-only trend workflows through `PatientVaultService`. This has started.
5. Route Activity/Audit history through `PatientVaultService`. This has started.
6. Add encrypted local vault storage. This has started as a development-only prototype.
7. Move report parsing internals fully into the vault service implementation.
8. Add encrypted export/import.
9. Add patient-owned cloud vault connector support.
10. Add clinician key wrapping and scoped sharing.

Until those steps are complete, hosted demos should use synthetic data only.
