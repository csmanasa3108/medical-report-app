# Synthetic Demo Hosting Plan

## 1. Hosting Goal

Host a public or semi-public SoveraHealth Diagnostic Reports demo so recruiters, collaborators, and testers can understand the product direction.

This demo must be synthetic-only:

- No real patient data.
- No real medical records.
- No real report PDFs.
- No production HIPAA, security, privacy, clinical, or compliance claims.
- No implication that the current prototype is ready for production medical use.

The demo should show the product workflow and patient-owned data direction without inviting users to upload or enter real PHI.

## 2. Recommended Demo Modes

### A. API Demo Mode

API demo mode runs the full hosted stack:

- Frontend hosted as a web app.
- Spring Boot backend hosted as an API.
- PostgreSQL hosted as the backing database.
- Synthetic reports only.
- Upload, parse, review, trends, activity, and patient/clinician workflows are easier to demonstrate.

Tradeoff: the central database and backend-managed storage still hold PHI-like synthetic data. This is acceptable only for clearly labeled synthetic demos. It is not aligned with the target patient-owned vault architecture for real data.

### B. Local Vault Demo Mode

Local vault demo mode hosts only the frontend as a static app:

- Frontend hosted publicly.
- `VITE_PATIENT_VAULT_MODE=local`.
- Patient creates/unlocks an encrypted browser vault.
- Manual observations are stored in the local encrypted vault.
- Trends read from the local encrypted vault.
- Export/import uses encrypted backup files.

Tradeoff: local mode currently supports manual observations and report metadata, but not browser-side PDF extraction. This mode is better aligned with the decentralized patient-owned architecture because local manual observations are not stored on the server.

## 3. Recommended First Deployment

Start with a frontend-only local vault demo.

Recommended first public path:

- Host the frontend only.
- Set `VITE_PATIENT_VAULT_MODE=local`.
- Do not connect a public backend yet.
- Use clear synthetic/demo-only warnings.
- Present API mode privately or locally until backend deployment safety gates are complete.

This path demonstrates the patient-owned vault direction while minimizing central PHI-like data storage risk.

## 4. Environment Variables

### Frontend

- `VITE_PATIENT_VAULT_MODE=api`: uses backend API-backed data access. This remains the default in development unless changed.
- `VITE_PATIENT_VAULT_MODE=local`: enables local encrypted vault mode.
- `VITE_API_BASE_URL`: should point to the deployed backend API for API mode. If the frontend currently hardcodes localhost, add this before hosting API mode.

Recommended demo values:

- Public frontend-only local vault demo: `VITE_PATIENT_VAULT_MODE=local`.
- Private full-stack API demo: `VITE_PATIENT_VAULT_MODE=api` and `VITE_API_BASE_URL=https://<demo-api-host>`.

### Backend

Backend deployment should use environment variables or platform secret settings for:

- Database URL, username, and password.
- Upload storage path, such as `app.uploads.reports-directory`.
- Auth/dev mode flags, including whether `X-User-Id` demo behavior is enabled.
- CORS allowed origins.
- Demo-only flags that restrict data and uploads.
- Spring profile, such as `SPRING_PROFILES_ACTIVE=demo`, if added.

Do not commit backend credentials, production secrets, private keys, or deployment-specific configuration.

## 5. Safety Gates Before Public Hosting

Before any public or semi-public hosted demo:

- Display a visible "synthetic/demo only" banner.
- Warn users not to upload or enter real medical data.
- Disable or restrict server-side upload if the API is public.
- Make the dev `X-User-Id` user switcher visibly demo-only.
- Ensure `X-User-Id` is not presented as real authentication.
- Ensure no secrets are committed.
- Ensure database credentials are provided through environment variables.
- Ensure CORS is not wildcard for public API deployments.
- Ensure uploads directory is not publicly exposed.
- Ensure uploaded files cannot be fetched directly without authorization checks.
- Seed only synthetic demo data.
- Clear or rotate any public demo database regularly.
- Ensure logs do not include report text, lab values, or extracted observations.
- Ensure local vault export/import warns users not to store real PHI.
- Confirm the app does not claim HIPAA compliance or production readiness.

## 6. Platform Options

### Frontend

- Vercel: simple React/Vite deployment, easy environment variables, good preview URLs.
- Netlify: similarly simple static hosting, good for solo prototypes.
- Cloudflare Pages: fast static hosting, strong free-tier option.
- S3/CloudFront: more AWS-native and durable, but more setup.

For a solo developer prototype, Vercel or Netlify is the fastest first step.

### Backend

- Render: simple Spring Boot hosting, easy environment variables, paired Postgres option.
- Fly.io: good for containerized apps, slightly more operational setup.
- Railway: fast prototype deployment, convenient Postgres, watch costs and lifecycle.
- AWS Elastic Beanstalk, ECS, or App Runner: better AWS path, more setup and policy work.

For a private API demo, Render or Railway is likely fastest. For longer-term AWS alignment, App Runner or ECS plus RDS is more appropriate.

### Database

- Render Postgres: convenient with Render backend.
- Railway Postgres: convenient with Railway backend.
- Supabase Postgres: easy managed Postgres with useful dashboard.
- AWS RDS PostgreSQL: strongest AWS production direction, more setup.

For a solo prototype, use the database attached to the backend hosting platform. Move to RDS only when the deployment model is more stable.

## 7. Deployment Phases

### Phase 1: Frontend-Only Local Vault Demo

- Host frontend only.
- Set `VITE_PATIENT_VAULT_MODE=local`.
- No backend required for local manual observations.
- Show local encrypted vault creation/unlock, manual observations, trends, activity, and encrypted export/import.
- Clearly label as synthetic/demo-only and prototype-only.

### Phase 2: Private API Demo

- Host backend and PostgreSQL privately or behind restricted access.
- Use synthetic PDF reports only.
- Use API mode to demonstrate upload, parse, review, trends, activity, and patient/clinician workflows.
- Keep access limited until safety gates are complete.

### Phase 3: Public API Demo With Tighter Controls

- Add public demo guardrails.
- Restrict upload or allow only known synthetic sample PDFs.
- Disable real PHI entry warnings in every relevant workflow.
- Lock CORS to known frontend origins.
- Keep demo data resettable and disposable.

### Phase 4: Patient-Owned Cloud Vault Connector

- Add a patient-owned encrypted cloud vault connector.
- Keep hosted backend as metadata/control plane only.
- Store central metadata and encrypted key envelopes, not raw PHI.
- Continue to avoid production readiness claims until real security and compliance work is complete.

## 8. Exact First Deployment Choice

Use Vercel or Netlify for the first public demo:

- Deploy `frontend`.
- Set `VITE_PATIENT_VAULT_MODE=local`.
- Do not configure a public backend connection.
- Add a visible Demo Safety Banner before publishing.
- Keep API-backed full-stack demo local/private for now.

This is the safest first public demo because local manual observations stay in the browser encrypted vault and are not sent to a central backend.

## 9. Risks And Limitations

- Browser `localStorage` is not production-grade vault storage.
- Lost passphrase means no recovery.
- Device/browser loss can mean data loss without an encrypted export backup.
- Local vault data is not synced across devices.
- Local vault mode is manual-observation focused until browser extraction exists.
- Browser-side encryption does not make the whole app production-ready.
- Server-side parsing should remain synthetic-demo-only.
- API mode centralizes synthetic PHI-like data in the demo backend and database.
- Dev role switching and `X-User-Id` are not real authentication.
- No HIPAA compliance or production security posture should be claimed.

## 10. Immediate Next Coding Task

The frontend now includes a visible demo safety banner and mode label in the app shell.

Current banner behavior:

- Always shows "Demo only - do not enter real medical data."
- Shows current mode: `API demo` or `Local encrypted vault prototype`.
- In local mode, warns that local vault data is encrypted in this browser only and lost passphrase means no recovery.
- In API mode, warns that API demo mode may store synthetic demo report data on the backend.

Next recommended coding task:

- Add an environment-controlled hosted-demo flag if the banner ever needs to be configurable by deployment target.
- Keep the banner enabled for public or semi-public demo builds.
