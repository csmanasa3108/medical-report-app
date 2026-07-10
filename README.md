# Medical Report App

Local Phase 1 app for manual lab observation entry and simple lab trend analytics.

## Requirements

- Java 21
- Maven
- Node.js and npm
- Docker or another local PostgreSQL 16-compatible server

## Start Postgres

From the repository root:

```bash
cd backend
docker compose up -d postgres
```

The local backend expects:

- database: `medical_reports`
- username: `medical_reports`
- password: `medical_reports`
- port: `5432`

## Start Backend

In a second terminal:

```bash
cd backend
./mvnw spring-boot:run
```

If `./mvnw` is not present, use:

```bash
cd backend
mvn spring-boot:run
```

The API runs on `http://localhost:8080`.

## Start Frontend

In a third terminal:

```bash
cd frontend
npm install
npm run dev
```

The Vite dev server prints the local URL, usually `http://localhost:5173`.

## Run Tests

Backend:

```bash
cd backend
mvn test
```

Frontend type-check/build:

```bash
cd frontend
npm run build
```

## Smoke Test Endpoints

Health:

```bash
curl http://localhost:8080/api/health
```

Tests catalog:

```bash
curl http://localhost:8080/api/tests
```

Create a manual observation. Replace `testId` with an id returned by `/api/tests`:

```bash
curl -X POST http://localhost:8080/api/observations \
  -H 'Content-Type: application/json' \
  -d '{
    "testId": "00000000-0000-4000-8000-000000000101",
    "observedAt": "2026-07-10",
    "numericValue": 12.8,
    "unit": "g/dL",
    "referenceLow": 12.0,
    "referenceHigh": 16.0,
    "abnormalFlag": "NORMAL"
  }'
```

Trend for a test:

```bash
curl http://localhost:8080/api/analytics/tests/00000000-0000-4000-8000-000000000101/trend
```

## Phase 1 Scope

This phase does not include PDF upload, OCR/Textract, AWS integration, or authentication.
