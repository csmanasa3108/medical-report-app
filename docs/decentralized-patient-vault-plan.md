# Decentralized Patient Vault Architecture Plan

## Purpose

SoveraHealth Diagnostic Reports currently works as a centralized application: the backend stores report metadata, extracted report content, parsed observations, confirmed observations, trends, access grants, and audit events in PostgreSQL. That is useful for a local demo, but it is not the target architecture for real patient medical data.

The target direction is a patient-owned vault model. The hosted SoveraHealth app should provide the user interface, identity, sharing workflow, coordination APIs, and minimal metadata, while each patient owns an encrypted health record vault. The hosted central database should not store raw reports, extracted text, lab values, parsed observations, confirmed observations, or trend points.

Until decentralized storage is implemented, the current centralized app should be hosted only with synthetic/demo data.

## 1. Current Centralized Model

The current app stores these medical-data workflows centrally:

- Reports: uploaded report metadata and, depending on implementation path, report files or file references.
- Parsed observations: extracted candidate rows from uploaded reports, including lab test names, values, units, dates, reference ranges, and review status.
- Lab observations: confirmed structured lab values used for trends.
- Trends: trend API responses are computed from centrally stored confirmed observations.
- Audit events: central audit records for app actions, including access grants, revokes, uploads, confirms, rejects, and deletes.
- Patient-clinician access: central grant rows that decide which clinicians can access which patients.

This model is straightforward for a prototype, but it makes the application database a PHI store. If real patient PDFs, extracted text, lab values, or trends are stored there, the backend database becomes a regulated medical data system.

## 2. Target Model

### Hosted Control Plane

The hosted control plane is the central SoveraHealth service. It should handle:

- User accounts and roles.
- Clinician profiles.
- Patient-clinician grant metadata.
- Public keys and encrypted key envelopes.
- Vault registration metadata.
- Non-PHI operational telemetry.
- Limited central audit for control-plane events.

The control plane should not store raw PHI.

### Patient-Owned Encrypted Data Vault

Each patient has an encrypted vault that contains their health record data:

- PDF report files or encrypted document blobs.
- Extracted report text.
- Parsed observation candidates.
- Confirmed observations.
- Trend point data.
- Detailed patient-local audit events.

The patient vault can begin as browser-local encrypted storage for a prototype, then move toward encrypted export/import and patient-owned cloud storage.

### Minimal Central Metadata Database

The central PostgreSQL database should eventually store only metadata required to coordinate the app:

- App user id, role, email, display name.
- Clinician access grant metadata.
- Public keys.
- Encrypted key envelopes.
- Vault manifest pointers, sync cursors, or storage provider references.
- Coarse audit events that do not include raw report text, lab values, or trend values.

### PHI Boundary

The central service should not receive or persist:

- Raw PDF report bytes.
- Extracted report text.
- Lab values.
- Reference ranges.
- Parsed observation candidates.
- Confirmed observations.
- Trend points.

## 3. Data Classification

| Data item | Current location | Future location | PHI risk | Notes |
| --- | --- | --- | --- | --- |
| PDF report file | Backend/database or backend-managed storage path, depending on current upload flow | Patient-owned encrypted vault | High | Contains raw clinical content. Do not centrally store in target model. |
| Extracted report text | Backend/database if extraction output is persisted | Patient-owned encrypted vault | High | Raw extracted text can contain names, dates, diagnoses, values, and identifiers. |
| Parsed observations | Central PostgreSQL | Patient-owned encrypted vault | High | Includes test names, values, units, dates, reference ranges, and flags. |
| Confirmed observations | Central PostgreSQL | Patient-owned encrypted vault | High | Structured lab values are PHI and should be patient-owned. |
| Trend points | Computed from central observations | Computed client-side or vault-side from encrypted observations | High | Trends are derived PHI and should not be centrally stored. |
| Audit events | Central PostgreSQL | Split: central control-plane audit plus patient-vault audit | Medium to High | Central audit should avoid raw PHI. Detailed vault actions can live inside the encrypted vault. |
| User accounts | Central PostgreSQL | Hosted control-plane DB | Low to Medium | Account metadata may still be sensitive, but it is not raw medical data. |
| Clinician access grants | Central PostgreSQL | Hosted control-plane DB | Medium | A grant can reveal care relationships. Store minimal metadata and avoid medical contents. |
| Public keys | Not yet fully implemented | Hosted control-plane DB | Low | Public keys are not secret, but integrity matters. |
| Patient vault metadata | Not yet implemented | Hosted control-plane DB | Low to Medium | Store only vault id, version, encrypted manifest pointer, storage provider, and sync metadata. Avoid filenames or medical labels if possible. |

## 4. Patient Vault Data Model

The vault data model should be FHIR-inspired without requiring full FHIR compliance in the first implementation. Use names and shapes that make future FHIR export easier, especially `DiagnosticReport`, `Observation`, and `DocumentReference`.

### PatientVaultManifest

```json
{
  "resourceType": "PatientVaultManifest",
  "vaultId": "vault_01H...",
  "patientUserId": "uuid",
  "schemaVersion": "2026-08-24.v1",
  "createdAt": "2026-08-24T12:00:00Z",
  "updatedAt": "2026-08-24T12:00:00Z",
  "documents": [
    {
      "documentReferenceId": "uuid",
      "diagnosticReportId": "uuid",
      "encryptedBlobRef": "local://documents/report-uuid",
      "contentType": "application/pdf",
      "sha256": "hex-encoded-digest"
    }
  ],
  "counts": {
    "documentReferences": 1,
    "diagnosticReports": 1,
    "observations": 12,
    "reviewItems": 3
  }
}
```

### ReportDocument

`ReportDocument` represents the encrypted source document payload and minimal non-secret metadata inside the vault.

```json
{
  "resourceType": "DocumentReference",
  "id": "uuid",
  "status": "current",
  "type": {
    "text": "Diagnostic report"
  },
  "subject": {
    "reference": "Patient/current"
  },
  "date": "2026-08-24T12:00:00Z",
  "content": [
    {
      "attachment": {
        "contentType": "application/pdf",
        "title": "diagnostic-report.pdf",
        "encryptedBlobRef": "local://documents/report-uuid",
        "hash": "sha256-digest"
      }
    }
  ],
  "context": {
    "source": "patient-upload",
    "uploadedAt": "2026-08-24T12:00:00Z"
  }
}
```

### DiagnosticReport

`DiagnosticReport` represents report-level clinical metadata and links to the source document and observations.

```json
{
  "resourceType": "DiagnosticReport",
  "id": "uuid",
  "status": "final",
  "subject": {
    "reference": "Patient/current"
  },
  "effectiveDateTime": "2026-08-06T00:00:00Z",
  "issued": "2026-08-06T12:00:00Z",
  "performer": [
    {
      "display": "Example Lab"
    }
  ],
  "presentedForm": [
    {
      "reference": "DocumentReference/uuid"
    }
  ],
  "result": [
    {
      "reference": "Observation/uuid"
    }
  ],
  "extension": {
    "originalFilename": "diagnostic-report.pdf",
    "parseStatus": "PARSED",
    "reviewStatus": "NEEDS_REVIEW"
  }
}
```

### Observation

`Observation` represents a confirmed structured result. It should map cleanly to future FHIR `Observation` export.

```json
{
  "resourceType": "Observation",
  "id": "uuid",
  "status": "final",
  "category": [
    {
      "text": "Laboratory"
    }
  ],
  "code": {
    "text": "Hemoglobin",
    "coding": [
      {
        "system": "soverahealth:test-catalog",
        "code": "hemoglobin"
      }
    ]
  },
  "subject": {
    "reference": "Patient/current"
  },
  "effectiveDateTime": "2026-08-06T00:00:00Z",
  "valueQuantity": {
    "value": 13.8,
    "unit": "g/dL"
  },
  "referenceRange": [
    {
      "text": "12.0-16.0"
    }
  ],
  "interpretation": [
    {
      "text": "H"
    }
  ],
  "derivedFrom": [
    {
      "reference": "DiagnosticReport/uuid"
    }
  ]
}
```

### ParsedObservationReviewItem

`ParsedObservationReviewItem` represents an extracted candidate before confirmation. It is not a final FHIR observation yet, but it should include enough structure to convert into `Observation`.

```json
{
  "resourceType": "ParsedObservationReviewItem",
  "id": "uuid",
  "status": "NEEDS_REVIEW",
  "diagnosticReportRef": "DiagnosticReport/uuid",
  "documentReferenceRef": "DocumentReference/uuid",
  "extracted": {
    "testName": "Hemoglobin",
    "valueText": "13.8",
    "numericValue": 13.8,
    "unit": "g/dL",
    "referenceRange": "12.0-16.0",
    "abnormalFlag": null,
    "observedAt": "2026-08-06T00:00:00Z"
  },
  "review": {
    "reviewedAt": null,
    "reviewedBy": null,
    "confirmedObservationRef": null,
    "rejectionReason": null
  },
  "createdAt": "2026-08-24T12:00:00Z"
}
```

### AuditEvent

Vault-local audit events can include detailed vault actions, but they should still avoid storing raw report text or unnecessary values.

```json
{
  "resourceType": "AuditEvent",
  "id": "uuid",
  "action": "PARSED_OBSERVATION_CONFIRMED",
  "actor": {
    "userId": "uuid",
    "role": "PATIENT"
  },
  "patient": {
    "reference": "Patient/current"
  },
  "entity": {
    "resourceType": "ParsedObservationReviewItem",
    "id": "uuid"
  },
  "occurredDateTime": "2026-08-24T12:00:00Z",
  "outcome": "success",
  "details": {
    "metadataOnly": true
  }
}
```

## 5. Encryption Model

### Patient Vault Encryption Key

Each patient vault should be encrypted with a high-entropy vault data encryption key. That key protects all PHI-bearing vault records and blobs. The key should never be stored in plaintext by the server.

Early prototype options:

- Derive or unlock the vault key in the browser after patient sign-in.
- Store only encrypted vault records in browser storage.
- Store an encrypted export backup that the patient can download and re-import.

Production direction:

- Use a patient-controlled key hierarchy.
- Protect the vault key with a key encryption key.
- Support key rotation and recovery workflows.
- Avoid making SoveraHealth support the only practical recovery path.

### Browser-Side Encryption and Decryption

The browser should encrypt PHI before it leaves the device and decrypt PHI only after the patient or authorized clinician unlocks the vault. The server should not receive plaintext PDFs, extracted text, lab values, parsed candidates, confirmed observations, or trend points.

The frontend should call a vault abstraction instead of directly calling report/observation APIs for PHI-bearing reads and writes.

### Server Key Handling Rules

- The server never sees plaintext PHI.
- The server never stores patient private keys.
- The server stores public keys and encrypted key envelopes only.
- The server may coordinate sync, sharing, and metadata if the payload remains encrypted.

### Encrypted Export and Import

Patients should be able to create an encrypted backup file containing:

- Vault manifest.
- Encrypted document references.
- Encrypted report blobs.
- Encrypted parsed review items.
- Encrypted confirmed observations.
- Encrypted local audit events.

Import should validate schema version, integrity hashes, and decryptability before replacing or merging a vault.

### Future Clinician Sharing With Key Wrapping

When the patient shares data, the browser can wrap either:

- The full vault key.
- A scoped collection key.
- A report-specific key.

The wrapped key is encrypted to the clinician public key and stored centrally as an encrypted key envelope.

## 6. Sharing Model

Patient-to-clinician sharing should work without placing plaintext PHI in the central database.

High-level flow:

1. Clinician has an app account and a registered public key.
2. Patient grants clinician access from the SoveraHealth UI.
3. Patient browser decides the sharing scope, such as whole vault, selected reports, or selected observations.
4. Patient browser encrypts the relevant vault key or scoped data key to the clinician public key.
5. Central DB stores grant metadata and encrypted key envelope only.
6. Clinician browser retrieves the encrypted vault payload and key envelope.
7. Clinician browser uses the clinician private key to unwrap the key and decrypt only shared data.

Central grant metadata can include:

- Grant id.
- Patient user id.
- Clinician user id.
- Status, such as `ACTIVE` or `INACTIVE`.
- Scope descriptor that avoids raw PHI.
- Encrypted key envelope id.
- Created and revoked timestamps.

The central service enforces grant metadata and envelope access, but it should not be able to decrypt the shared medical data.

## 7. Audit Tradeoff

Decentralized PHI changes the audit model.

Central audit can reliably record:

- Account sign-in or session events, if auth supports it.
- Patient-clinician access grants and revokes.
- Key envelope creation, retrieval, and revocation metadata.
- Vault sync requests.
- Export/import coordination metadata if performed through the app.

Central audit should not store:

- Raw report text.
- Lab values.
- Trend values.
- Report filenames if filenames can contain PHI.
- Detailed parsed observation contents.

Patient vault audit can record detailed local actions:

- Report added.
- Text extracted locally.
- Parsed observation created.
- Observation confirmed or rejected.
- Trend viewed.
- Export created.

However, server-side audit cannot fully prove what happened inside a patient-owned vault. If the patient decrypts and modifies local data offline, the central server may see only later sync metadata. This is a product and compliance tradeoff that should be documented clearly.

## 8. Migration Strategy

### Phase 1: Freeze Current Centralized PHI Model for Demo Only

- Keep existing backend tables and demo workflows.
- Clearly label the centralized model as demo/synthetic-data only.
- Do not host real patient PHI on the current centralized stack.
- Avoid expanding central storage of report text, lab values, or trends.

### Phase 2: Introduce Frontend Vault Abstraction

- Add a frontend data-access boundary, such as `PatientVaultService`.
- Route PHI-bearing report, parsed observation, confirmed observation, and trend reads through the abstraction.
- Keep existing backend APIs behind a demo adapter.
- Add a vault-mode adapter for patient-owned storage.

### Phase 3: Move Reports, Observations, and Trends Into Local Encrypted Vault

- Store new report documents in encrypted browser-local vault storage.
- Store extracted text, parsed review items, confirmed observations, and computed trend points in the vault.
- Compute trends from decrypted local observations in the browser.
- Keep the backend centralized PHI path available only for demo mode.

### Phase 4: Reduce Backend DB to Metadata and Control Plane

- Stop writing new PHI-bearing records to central tables in patient-vault mode.
- Keep central DB tables for users, grants, public keys, envelopes, and vault metadata.
- Keep existing centralized tables only for local demo compatibility until a cleanup milestone.

### Phase 5: Encrypted Export and Import

- Implement encrypted backup creation.
- Implement import validation and vault restore.
- Include schema migration logic for vault files.
- Document recovery limitations.

### Phase 6: Patient-Owned Cloud Vault Connector

- Add a connector model for patient-owned storage, such as patient-controlled cloud object storage or a personal data store.
- Store only encrypted vault objects remotely.
- Keep SoveraHealth as the UI/control plane, not the PHI database.

### Phase 7: Clinician Sharing With Encrypted Key Wrapping

- Add clinician public key registration.
- Add patient-side key wrapping.
- Store encrypted key envelopes centrally.
- Allow clinician browser access only to shared encrypted vault data.
- Support grant revocation by removing envelope access and rotating scoped keys where practical.

### Phase 8: Future SMART on FHIR Integration

- Map vault `DiagnosticReport`, `DocumentReference`, and `Observation` structures toward FHIR export.
- Support patient-authorized import/export through SMART on FHIR where appropriate.
- Keep the patient vault as the source of patient-owned data.

## 9. Hosting Strategy

The frontend can be hosted as a static app. It should contain the patient vault UI, local encryption/decryption logic, and data-access adapters.

The backend can be hosted as an API/control plane. It should provide:

- User management.
- Role-aware app coordination.
- Clinician directory and access workflow.
- Public key and encrypted key envelope metadata.
- Vault metadata and sync coordination.
- Limited metadata-only audit.

The hosted database should store only metadata. It should not store raw reports, extracted text, lab values, parsed observations, confirmed observations, or trend data once patient-vault mode is active.

Demo hosting should use synthetic data only. The current centralized app should not be presented as ready for real patient PHI.

## 10. Engineering Risks

- Lost encryption key means data loss unless a recovery model exists.
- Browser storage has quota, persistence, eviction, and backup limitations.
- Multi-device sync requires conflict handling, encryption-compatible merges, and careful metadata design.
- Clinician sharing is complex because key envelopes, scope, revocation, and rotation must be correct.
- OCR/PDF parsing may expose PHI if done server-side. Local parsing or a patient-controlled processing path is safer for the target model.
- Backups and recovery are harder because the server cannot decrypt patient data.
- Support and debugging are harder because engineers cannot inspect plaintext production data.
- Search and analytics are harder because central queries cannot read encrypted PHI.
- Revocation cannot erase data a clinician already decrypted or exported.
- Browser-side cryptography must be implemented carefully with standard Web Crypto APIs and a reviewed key lifecycle.

## 11. Immediate Implementation Plan

Recommended next coding steps:

1. Create a frontend data-access abstraction for PHI-bearing workflows.
2. Create a `PatientVaultService` interface with methods for reports, parsed review items, confirmed observations, trends, and vault audit events.
3. Add a demo adapter that calls the existing backend APIs so current demo behavior remains intact.
4. Add a local encrypted vault prototype adapter for patient-vault mode.
5. Move parsed observation and trend reads behind the vault service first, since they are central to the product workflow.
6. Move report upload flow behind the vault service and stop storing new report PDFs in the backend when patient-vault mode is enabled.
7. Add vault-local audit events for confirm, reject, upload, parse, delete, export, and import actions.
8. Add encrypted export/import before relying on browser-local storage for anything valuable.
9. Add central metadata models for public keys, vault metadata, and encrypted key envelopes only after the local vault prototype works.
10. Keep the existing backend PHI tables for demo compatibility until a deliberate cleanup phase.

## Non-Goals for This Plan

- Do not implement encryption yet.
- Do not delete current centralized tables.
- Do not modify backend or frontend code as part of this planning step.
- Do not add dependencies.
- Do not claim production readiness for real patient PHI.
