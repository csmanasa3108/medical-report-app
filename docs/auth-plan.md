# Authentication and Authorization Plan

## Current State

SoveraHealth Diagnostic Reports currently uses a development authentication model. The React app sends an `X-User-Id` header with API requests, and the Spring Boot backend resolves the current user from that UUID. If the header is missing, the backend falls back to the Demo Patient user.

The app already has useful authorization foundations:

- `app_users` stores app-level user profiles with `PATIENT` and `CLINICIAN` roles.
- `patient_clinician_access` controls which clinicians may access which patients.
- Reports and lab observations are scoped to patients.
- Backend services/controllers enforce patient and clinician access checks.
- The frontend dev user switcher helps local testing by changing the `X-User-Id` header.

Safe to keep:

- `app_users` as the app profile and role table.
- `patient_clinician_access` as the clinician-patient assignment table.
- Backend authorization checks for patient-owned data and clinician assignments.
- Tests that verify patient and clinician access boundaries.

Dev-only:

- Trusting `X-User-Id` as the current user.
- Falling back to Demo Patient when no user identity is provided.
- Frontend dev user switcher as a normal user selection mechanism.

## Target Auth Model

Production requests should come from real authenticated users. Patients and clinicians should sign in through a real auth system, and the backend should derive the current app user from a verified authentication identity rather than a frontend-selected UUID.

Target flow:

- User signs in through an auth provider.
- Frontend receives an authenticated session/token.
- Frontend sends `Authorization: Bearer <token>` on API requests.
- Backend validates the token.
- Backend maps token claims, such as subject and/or email, to an `app_users` row.
- Backend authorization continues to use the resolved `app_users.id`, role, and `patient_clinician_access`.

The frontend must not be trusted to choose a role, patient identity, clinician identity, or access scope.

## Recommended MVP Auth Approach

Use a hosted auth provider or OAuth/OIDC-compatible provider when moving beyond local development. The exact provider can be selected later, but the app should prepare for a standard bearer-token model.

MVP behavior:

- Frontend signs the user in through the provider.
- Frontend stores no medical report text, lab values, or patient data in `localStorage`.
- Frontend sends `Authorization: Bearer <token>`.
- Backend validates token signature, issuer, audience, and expiration.
- Backend resolves the current app user from token claims.
- Backend rejects missing, invalid, or expired tokens with `401` once real auth is enabled.

## Backend Implementation Phases

### Phase A: Isolate Dev Auth

- Put `X-User-Id` support behind a local/dev profile or explicit property.
- Keep Demo Patient fallback only in local development.
- Make production/staging fail closed when no valid auth identity is present.

### Phase B: Add Current User Abstraction

- Introduce a small current-user abstraction used by services/controllers.
- Keep controllers independent from whether the user came from dev header auth or token auth.
- Preserve existing patient and clinician authorization services.

### Phase C: Add Token-Based Resolver

- Validate bearer tokens in the backend.
- Extract stable auth identity from verified claims.
- Resolve the identity to `app_users`.
- Return `401` for missing/invalid auth and `403` for authenticated users without required access.

### Phase D: Disable X-User-Id Outside Local Development

- Reject or ignore `X-User-Id` when not in local/dev mode.
- Ensure Demo Patient fallback cannot run in production-like environments.
- Keep any local-only behavior clearly named and property-gated.

### Phase E: Update Access-Control Tests

- Keep current access-control regression coverage.
- Add tests for dev auth only under local/dev configuration.
- Add token-auth tests that verify current user resolution from token claims.
- Verify that `X-User-Id` does not work outside local/dev mode.

## Frontend Implementation Phases

### Phase A: Local-Only Dev Switcher

- Keep the dev user switcher only in local mode.
- Hide or remove it from production builds.
- Make API client behavior environment-aware.

### Phase B: Login/Logout Shell

- Add sign-in, sign-out, and authenticated-session state.
- Show unauthenticated users a login screen.
- Clear in-memory auth/session state on logout.

### Phase C: Authorization Header

- Attach `Authorization: Bearer <token>` to authenticated API requests.
- Avoid storing medical data in `localStorage`.
- Handle `401` by prompting for login again.

### Phase D: Remove Normal X-User-Id Calls

- Stop sending `X-User-Id` for normal production API requests.
- Retain `X-User-Id` only for explicit local development mode.

### Phase E: Role-Based Dashboard

- Load the authenticated app user profile from the backend.
- Show patient or clinician workflows based on backend-resolved role.
- Do not use frontend-selected roles for authorization decisions.

## Data Model Notes

- Keep `app_users` as the app profile table.
- Keep `email` unique.
- Keep `role` in `app_users`.
- Add an `auth_subject` column if the auth provider supplies a stable subject identifier.
- Map auth provider identity to `app_users` using `auth_subject` when available, with email as a secondary lookup or provisioning input.
- Keep `patient_clinician_access` as the source of truth for clinician access to patients.
- Continue scoping reports and lab observations by `patient_user_id`.

## Security Rules

- Never trust a frontend-selected role.
- Never trust a frontend-selected patient without backend authorization.
- Dev `X-User-Id` must not work in production.
- Clinician access must always check `patient_clinician_access`.
- Patient data must always be scoped by `patient_user_id`.
- Avoid logging extracted report text, lab values, or other medical data.
- Return `401` for unauthenticated requests and `403` for authenticated users without required access.

## Test Plan

- Patient can access only own reports, observations, trends, and parsed report data.
- Patient cannot list clinician patients.
- Clinician can access only assigned patients.
- Clinician access to an unassigned patient returns `403`.
- Clinician patient-scoped endpoints require `patientId` when needed.
- Missing token returns `401` once real auth is enabled.
- Invalid or expired token returns `401`.
- Valid token with no matching `app_users` row returns a controlled `401` or `403`, depending on provisioning policy.
- `X-User-Id` works only in local/dev mode.
- `X-User-Id` is rejected or ignored outside local/dev mode.

## Out of Scope for Now

- Billing.
- Password reset implementation details.
- Organization admin console.
- HIPAA/legal certification work.
- Production deployment hardening.
