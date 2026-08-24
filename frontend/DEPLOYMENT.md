# Frontend Deployment Notes

## Recommended First Hosted Mode

Use the frontend-only local encrypted vault demo first.

- Build command: `npm run build`
- Output directory: `dist`
- Required environment variable: `VITE_PATIENT_VAULT_MODE=local`
- Backend required: no, not for local manual-observation demo workflows

This mode lets users create an encrypted browser vault, add manual observations, view local trends, and export/import encrypted backups without sending local observations to the backend.

## Required Demo Warnings

This hosted demo must not be used with real medical data.

- Do not enter real patient data.
- Do not upload real medical records.
- Do not claim HIPAA compliance.
- Do not describe the prototype as production-ready.

The app shell includes a visible demo safety banner and vault mode label. Keep it visible for public or semi-public demos.

## Environment Variables

Recommended public frontend-only demo:

```bash
VITE_PATIENT_VAULT_MODE=local
VITE_API_BASE_URL=
```

API-backed private demo:

```bash
VITE_PATIENT_VAULT_MODE=api
VITE_API_BASE_URL=https://your-demo-api.example.com
```

Notes:

- `local` mode is recommended for the first public demo.
- `api` mode requires a deployed backend and PostgreSQL database.
- If `VITE_API_BASE_URL` is empty, the frontend falls back to `http://localhost:8080` for API mode.

## Local Vault Limitations

- Local vault data is browser/device specific.
- Local vault data is not synced across devices.
- Lost passphrase means no recovery.
- Browser storage loss can mean data loss without an encrypted backup export.
- The current local vault demo supports manual observations and report metadata.
- Browser-side report extraction is not implemented yet.
- `localStorage` is not production-grade vault storage.

## API Mode Limitations

API mode is useful for private synthetic demos of upload, parsing, review, and trends.

- Use synthetic reports only.
- API mode may store synthetic demo report data on the backend.
- Do not expose API upload publicly until safety checks are complete.
- Keep CORS locked to known frontend origins.
- Keep credentials in environment variables or platform secrets.

## Pre-Deployment Checklist

- Set `VITE_PATIENT_VAULT_MODE=local` for the first public frontend-only demo.
- Confirm `npm run build` passes.
- Confirm the demo safety banner is visible.
- Confirm Add Observation, Trends, Activity, Reports, and Vault Settings work without a backend in local mode.
- Confirm API-only pages show clean prototype messages in local mode.
- Confirm no secrets or private environment files are committed.

