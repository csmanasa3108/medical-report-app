# SoveraHealth Diagnostic Reports

SoveraHealth Diagnostic Reports is a full-stack diagnostic report management prototype. It supports uploading diagnostic reports, extracting parsed lab observations, reviewing and confirming or rejecting extracted results, and trending confirmed observations over time.

The app includes patient and clinician demo roles, a Care Team access workflow, an activity/audit history, and an early local encrypted patient vault prototype that explores a decentralized patient-owned data architecture.

## Demo Safety Notice

**Demo/prototype only. Do not enter real medical data.**

- This repository is not production-ready.
- This repository does not claim HIPAA compliance.
- Do not upload real medical records, real patient data, or real report PDFs.
- API demo mode may store synthetic demo report data on the backend and in PostgreSQL.
- Local vault mode encrypts data in the browser, but it is still prototype-only and not production-grade vault storage.

## Current Features

- Report upload and report library
- PDF text extraction and parsed observations
- Review Queue for extracted results
- Confirm/reject parsed observations
- Trends for confirmed observations over time
- Source report linkage for report-derived trend points
- Manual observations
- Activity/audit log
- Care Team / clinician access management
- Patient and clinician demo views
- Local encrypted patient vault prototype
- Encrypted local vault export/import backup
- Local manual observation add/edit/delete workflow

## Architecture Overview

### Frontend

- React
- TypeScript
- Vite
- Role-aware app shell and demo user switcher
- `PatientVaultService` abstraction for API-backed and local-vault data access

### Backend

- Java 21
- Spring Boot
- REST APIs
- PostgreSQL
- Flyway migrations
- Spring Data JPA
- PDF extraction with Apache PDFBox

### Modes

**API demo mode**

- Frontend calls the Spring Boot backend.
- Backend stores demo reports, parsed observations, confirmed observations, audit events, and access metadata in PostgreSQL.
- Best for demonstrating upload, extraction, review, trends, Care Team, and clinician workflows with synthetic data.

**Local encrypted vault mode**

- Frontend runs as a static app.
- Patient creates/unlocks an encrypted browser vault.
- Manual observations, local trends, local audit events, and encrypted export/import stay in the browser.
- Better aligned with the patient-owned data direction, but still prototype-only.

## Data Privacy Direction

The current API mode is a functional synthetic demo path. It is useful for showing report upload, parsing, review, and trend workflows, but it centralizes PHI-like demo data in the backend and database.

The long-term product direction is decentralized patient-owned data:

- The hosted backend should become a control plane for identity, sharing, metadata, and encrypted key coordination.
- Patient medical data should move toward encrypted patient-owned vaults.
- Raw reports, extracted text, lab values, parsed observations, and trend points should not be centrally stored in the future architecture.
- Clinician sharing should eventually use encrypted key wrapping and scoped access.

## Tech Stack

| Area | Technology |
| --- | --- |
| Frontend | React, TypeScript, Vite |
| Backend | Spring Boot, Java 21, Maven |
| Database | PostgreSQL, Flyway |
| Charts | Custom React/SVG trend charts in the current frontend |
| Storage | Local filesystem for backend report uploads; local encrypted browser vault prototype |
| Dev | Docker Compose for PostgreSQL |

## Repository Structure

```text
frontend/   React + TypeScript + Vite app
backend/    Spring Boot API, PostgreSQL integration, Flyway migrations
docs/       Architecture notes, hosting plans, auth plan, vault plan
README.md   Project overview and local setup
```

## Local Development Setup

### Backend

Start PostgreSQL:

```bash
cd backend
docker compose up -d
```

Run the Spring Boot API:

```bash
cd backend
mvn spring-boot:run
```

The local API runs on `http://localhost:8080`.

### Frontend

Install dependencies and start Vite:

```bash
cd frontend
npm install
npm run dev
```

The Vite dev server prints the local frontend URL.

### API Mode Environment

Use API mode when running the backend locally:

```bash
VITE_PATIENT_VAULT_MODE=api
VITE_API_BASE_URL=http://localhost:8080
```

### Local Vault Mode Environment

Use local vault mode for frontend-only vault workflows:

```bash
VITE_PATIENT_VAULT_MODE=local
```

Restart Vite after changing `.env.local` or any Vite environment variable.

## Running Tests And Builds

Backend tests:

```bash
cd backend
mvn clean test
```

Frontend production build:

```bash
cd frontend
npm run build
```

## Demo Users

The frontend includes a development user switcher for local/demo workflows:

- Demo Patient
- Demo Clinician

The current development auth model sends an `X-User-Id` header to the backend. This is demo-only plumbing, not production authentication. It must be replaced before any production use.

## Key Workflows

### API Mode

- Upload a synthetic diagnostic report.
- Extract parsed observations from report text.
- Review extracted results in the Review Queue.
- Confirm or reject parsed observations.
- View confirmed observation trends.
- Open source reports from trend points.
- Review Activity/audit history.
- Manage Care Team clinician access.

### Local Vault Mode

- Create or unlock a local encrypted vault.
- Add a manual observation.
- View local trends from confirmed manual observations.
- Edit or delete local manual observations.
- View local Activity events where supported.
- Export/import an encrypted vault backup.

## Hosting Strategy

Recommended first fully functional low-cost hosting path for a synthetic demo:

- AWS Amplify for the frontend.
- AWS Lightsail for Spring Boot + PostgreSQL.
- S3 later for private report file storage.

Recommended frontend-only privacy demo:

- Host the frontend as a static app, such as with AWS Amplify.
- Set `VITE_PATIENT_VAULT_MODE=local`.
- Do not require a backend for the first public local-vault demo.
- Show the demo safety banner and mode label.

Keep real medical data out of the app until the security, privacy, authentication, storage, audit, and patient-owned vault architecture are production-ready and independently reviewed.

## Roadmap

- Browser-side report extraction for local vault mode
- S3/private file storage abstraction for API mode
- Real authentication and authorization
- Replace dev `X-User-Id` behavior
- Patient-owned cloud vault sync
- Clinician sharing with encrypted key wrapping
- SMART on FHIR integration
- Production-grade security and privacy review

## Status

Active prototype.

Not production-ready. Synthetic/demo data only. Do not enter real medical data.
