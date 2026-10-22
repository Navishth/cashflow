# CashFlow implementation status

Updated: 2026-10-21. PASS means observed local behavior; it does not mean production certification.

## Brand update — 22 October 2026

The product name is now CashFlow. Updated website copy/title, auth and mail text, project docs, Python service title, frontend package name, Maven group/artifact and JAR references. Renamed the Java source package and paths to `com.cashflow`. Frontend production build and backend package build passed with tests skipped; no test suite was run for this branding-only request. On 22 October, started the new packaged backend and verified it returns `UP`, serves the updated auth options contract, and works with the already-healthy frontend and model service. The unused stale ignored build JAR remains locked by Windows; scripts and Docker packaging use the new artifact.

## Google OAuth follow-up — paused at user request

On 21 October 2026 the user requested Google sign-in, then explicitly paused setup until later. No Google Cloud client has been supplied, no real credentials were imported, and Google sign-in remains disabled. The user subsequently requested shutdown, then asked to run the renamed project; the frontend and ML service were found healthy and the new backend was started. The app is currently available at `http://localhost:5173`.

Source changes prepared: centralized Google configuration with complete-credential validation, explicit `GOOGLE_REDIRECT_URI`, canonical authorization/callback origin, frontend authorization link supplied by the backend, and `scripts/configure-google.ps1` to import a downloaded Web application client JSON into ignored local configuration without printing secrets. Google requires a Web application client with local callback `http://localhost:5173/login/oauth2/code/google`.

Verification completed before the pause: 21 backend tests passed (including OAuth state tampering, consent cancellation and callback configuration); 14 frontend tests passed. The OAuth code is included in the current packaged JAR and frontend build; real Google sign-in remains disabled until a client is configured. The credential importer still needs validation with synthetic fixtures; its referenced `docs/GOOGLE_AUTH.md` has not yet been written, and deployment/API documentation needs updating for the new settings and options response.

Resume only when the user asks: finish importer/documentation verification, help create the Google Cloud Web application client, import the user's downloaded JSON by local file path, rebuild/restart, and verify the real provider login with the user. Preserve the original email-account collision protection. The milestone evidence below describes the earlier verified running build unless superseded above.

## Environment

The workspace was initially empty. The user build prompt and reference Word specification were read, Git initialized, and the requested architecture implemented. No previous user code was replaced.

Verified tools: Java 21.0.3, Maven Wrapper 3.9.11, Node 24.19.0, workspace npm 12.2.0, Python 3.11.9, PostgreSQL 17.11. Python 3.12 is the Docker target but has not been executed here. Docker CLI exists; its engine is stopped. Local PostgreSQL uses only this project's `.local/postgres` cluster on port 55432. Secrets and development mail remain under ignored `.local/`.

## Milestone audit

| Milestone | State | Evidence and limits |
| --- | --- | --- |
| 1. Foundation | PARTIAL | All three services start through `scripts/start-local.ps1`; health checks pass. Stop script closes only its recorded processes. Flyway applied V1 in application and test schemas. Compose configuration validates; container execution unavailable without a running Docker engine. |
| 2. Authentication/business | PARTIAL | Browser registration, local verification, login, onboarding, refresh persistence, and logout passed. API tests cover hashing, CSRF, expiry, single-use tokens, recovery, session invalidation, and Google subject/email separation. External Google and SMTP delivery require credentials. |
| 3. Financial records | PASS | PostgreSQL-backed CRUD/validation/ownership tests, month-end/leap-year recurrence, cancellation/replacement, and duplicate prevention pass. Browser creation/editing and monthly commitment creation pass; deletion confirmation inspected and cancelled. API deletion tests pass. |
| 4. ML/forecast | PASS locally | Seeded training/evaluation, 14 Python tests, real Java-to-Python inference, 30-day calculation, stale snapshots, minima and reserve dates verified. Demo model version `synthetic-hgbr-v1-1374d58a72f2`; real businesses use an explicitly labeled baseline. |
| 5. Replay/frontend | PASS locally | Browser scenario creation, edit, simulation, refresh and reopen passed; API tests prove repeatability and actual-record immutability. Live CRUD changes appear after forecast refresh. Responsive checks at 1440, 390 and 320 pixels; mobile overflow fixed. |
| 6. Security/deployment | PARTIAL | 15 backend, 14 Python, and 10 frontend tests passed. Strict TypeScript/production build passed. npm and Python audits clear. Patched Java build and live retest passed; all 104 packaged runtime coordinates audited, with two conditional Spring Web MVC advisories retained and assessed in SECURITY.md. External production integrations and container runtime remain unverified. |

## Verification evidence

- `scripts/backend.ps1 -MavenArgs @('-B','-ntp','package')`: 15 passing tests, executable JAR. Added tests for expired/superseded tokens, Google identity collisions, real CSRF tokens, ML HTTP format/read timeout, body limits and spoofed-forwarded-IP rate-limit bypass.
- `scripts/frontend.ps1 -NpmArgs @('test')`: 10 passing tests, including session hydration, protected routes, API error recovery, onboarding and empty forecast behavior.
- `scripts/frontend.ps1 -NpmArgs @('run','build')`: strict TS and production build passed, with separate chart/form chunks.
- `python -m pytest -q` in `ml-service/`: 14 passing tests. Two upstream test-client deprecation warnings; no failed tests. Windows socket tests required execution with local network permission.
- `python -m training.generate` / `python -m training.train`: reproducible generated data, trained artifact, chronological validation/test results. See MODEL_EVALUATION.md and `ml-service/artifacts/evaluation.json`.
- `scripts/start-local.ps1`: all service health checks passed with the packaged Java JAR and compiled frontend served by Vite preview. Browser login, dashboard and saved replay passed against the compiled `/assets/` bundle. `scripts/stop-local.ps1`: all recorded service listeners closed. An unused SMTP health check initially blocked file-mail development startup; fixed and covered by a backend health assertion.
- `scripts/smoke.py`: registration, verification, rotated session, business setup, live trained 30-day inference, persisted/repeatable scenario, unchanged actual records, CORS allowlist and logout invalidation all passed again on the final patched backend. Safe result in `.local/smoke-result.json`.
- Browser: synthetic Marigold Studio demo. Moving its INR 125,000 receipt from 2 November to 18 November lowered the minimum by INR 77,061.77 without changing the horizon's closing balance. Reopened after refresh and backend restart with identical results. The older-baseline notice correctly remains after later financial changes.
- Browser: a demo cash flow changed from INR 10,000 to INR 12,000.50, a monthly INR 1,200 commitment was added, stale-forecast notice appeared, and refreshed figures included both changes. These are isolated synthetic records.
- Browser: desktop and mobile navigation/forms/charts inspected; page-width overflow corrected, reserve line made visible outside the data range, compact axis precision improved. Screenshots in `docs/screenshots/`.
- `docker compose config --quiet`: passed using generated local environment variables; no container build/run claimed.
- Dependency audits: npm reports zero advisories; pip-audit reports zero for the 33-package application/test lockfile. The final Java OSV audit checked 104 runtime coordinates with no unidentified JARs and retained two advisories affecting Spring Web MVC 6.2.19. Their required rendering features are absent from this JSON API; the exposure assessment and links are in SECURITY.md. The Java audit intentionally exits nonzero while these findings remain. Reports are under ignored `.local/`.

## Security and model boundaries

Ownership is enforced through the authenticated user and business for every private resource. Monetary authority is Java BigDecimal/PostgreSQL NUMERIC. Scenarios use immutable forecast snapshots and never write actual flows. CSRF remains enabled; no browser access to PostgreSQL or the private ML API.

The trained model is synthetic and only enabled for explicitly chosen sample businesses. It slightly improves test MAE but has worse RMSE than the baseline; neither real-world accuracy nor confidence percentages are claimed. Missing/invalid/slow inference yields a labeled deterministic baseline.

## Remaining configuration and limitations

- BLOCKED: live Google sign-in needs a Google client ID/secret and registered callback; SMTP delivery needs provider configuration. The implementation and local identity/security behavior are tested, external delivery/authentication are not.
- BLOCKED: Docker runtime tests need a running Docker engine. Python 3.12 deployment execution and Nginx container behavior remain unverified.
- PARTIAL: production HTTPS, backups/restore, monitoring and hosting need an actual target environment. No public deployment or bank/payment integration was performed.
- PARTIAL: sessions/rate limits are in one process. Use shared stores before replicas. Local sessions intentionally expire on backend restart.
- PARTIAL: Java scan reports Spring 6.2.19 advisories for SSE view fragments and XsltView. This JSON API uses neither. Maven Central has no newer 6.2 patch at inspection time; preserve required Spring Boot 3 and record the exposure review in SECURITY.md rather than silently suppressing findings.

## Local handoff and next exact actions

The local build and integration checks are complete; Google OAuth setup remains paused as described above. The renamed frontend and backend are running at `http://localhost:5173` and `http://127.0.0.1:8080`; the inference service is healthy on port 8001. To stop the project, use `.\scripts\stop-local.ps1 -Database`. To start it after stopping, use `.\scripts\start-local.ps1` from PowerShell 7 in this workspace. Register and use the verification message in `.local/mail/`; choose the sample business to explore synthetic model inference. Full setup and rebuild commands are in README.md. Synthetic verification records are preserved in the isolated local database.

Production follow-up, in order:

1. Supply a hosting/HTTPS target, SMTP configuration, and Google client credentials/callback using the variables in `.env.example` and DEPLOYMENT.md. Verify actual email delivery and provider sign-in.
2. Start Docker Engine, build and run Compose, then verify Python 3.12, Nginx routing/headers, migrations and the same integration flows inside containers.
3. Resolve the organization's acceptance or remediation of the two retained Spring advisories, use a current patched Java 21 runtime, and complete independent security review, backup/restore and monitoring checks before public production use.
