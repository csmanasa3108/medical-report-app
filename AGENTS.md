You are working on a full-stack medical report analytics app.

Project goal:
Users upload medical reports, usually PDFs. The app stores structured lab observations such as test name, test date, value, unit, and reference range. When the same test appears across multiple dates, the app shows trends and comparisons.

Tech stack:

* Frontend: React, TypeScript, Vite
* Backend: Java 21, Spring Boot 3
* Database: PostgreSQL
* Migrations: Flyway
* ORM: Spring Data JPA
* Charts: Recharts
* Cloud target later: AWS S3, RDS PostgreSQL, SQS, Textract

Important product rules:

* Do not implement PDF/OCR extraction until explicitly requested.
* First build manual lab value entry and trend analytics.
* Treat medical data as sensitive.
* Do not log medical values.
* Scope all user data by user_id.
* Prefer small, testable changes.
* Do not introduce unnecessary frameworks.
* Do not rewrite unrelated files.
* Do not generate huge abstractions before they are needed.

Current phase:
Phase 0/1 only. Build local app foundation, report metadata, manual lab observation entry, and simple trend graph. PDF upload and AWS come later.

Coding rules:

* Backend packages should be organized by feature.
* Use DTOs for API request/response objects.
* Do not expose JPA entities directly from controllers.
* Use Flyway migrations for database schema.
* Add basic tests for services/controllers when practical.
* Keep code simple and readable.

Before making large changes:

* Inspect the repo.
* Explain the smallest safe plan.
* Ask for confirmation before broad refactors.

