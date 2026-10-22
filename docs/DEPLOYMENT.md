# Deployment configuration

The database contributes to `/actuator/health`. SMTP checks default off so the development mail spool does not require an SMTP server. Set `SMTP_HEALTH_ENABLED=true` with a configured SMTP provider to include mail connectivity in the health check.

## Topology

Serve the frontend and `/api`, `/oauth2`, `/login/oauth2` through one HTTPS origin when possible. Separate frontend/backend subdomains of the same registrable domain also work with an exact credentialed CORS allowlist. Unrelated hosting domains face third-party-cookie restrictions; do not assume SameSite=None bypasses browser privacy rules.

The provided Compose stack is a local topology. It publishes only the frontend on loopback port 5173, with PostgreSQL/Python on an internal network. Production needs a TLS ingress and production SMTP. No particular hosting provider/free tier has been selected or claimed compatible.

## Environment

| Variable | Purpose |
| --- | --- |
| `DATABASE_URL` | JDBC PostgreSQL URL; use provider-required TLS, e.g. `?sslmode=verify-full` with trusted CA |
| `DATABASE_USER`, `DATABASE_PASSWORD` | Dedicated application DB credentials |
| `ML_URL`, `ML_API_KEY` | Private inference URL and random key of at least 32 chars |
| `FRONTEND_URL` | Fixed post-login destination and email link origin |
| `ALLOWED_ORIGINS` | Comma-separated exact frontend origins, no wildcards |
| `COOKIE_SECURE` | `true` under production HTTPS |
| `APP_PRODUCTION` | `true` rejects insecure frontend/cookie and local mail mode |
| `MAIL_MODE` | `smtp` for production; `file` only for development |
| `MAIL_DIRECTORY` | Development-only spool directory, never public |
| `MAIL_FROM` | Verified sending address/domain |
| `SMTP_HOST`, `SMTP_PORT`, `SMTP_USER`, `SMTP_PASSWORD` | Provider settings |
| `SMTP_AUTH`, `SMTP_TLS` | Usually `true` for authenticated STARTTLS |
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | Backend-only OAuth credentials |
| `API_DOCS_ENABLED` | Disable public production API docs; docs require authentication by default |
| `BIND_ADDRESS`, `PORT` | `0.0.0.0` inside containers; port defaults 8080 |
| `MODEL_PATH` | Python's operator-controlled model artifact path |
| `VITE_API_URL` | Frontend public API origin; empty for same-origin proxy. Never a secret. |

If a trusted TLS proxy terminates HTTPS, configure Spring's forwarded-header handling for that environment and ensure the edge **strips client-supplied forwarding headers**. Do not expose a forwarding-trusting backend directly to the internet.

For separately hosted frontends, rebuild with VITE_API_URL and update CSP's `connect-src` to the exact backend origin.

## Google

Create a web OAuth client in Google Cloud. Configure authorized callback URI:

- Direct local backend: `http://127.0.0.1:8080/login/oauth2/code/google` or the exact hostname actually used.
- Through the frontend proxy: `http://localhost:5173/login/oauth2/code/google`.
- Production: `https://api.your-domain.example/login/oauth2/code/google` or the same-origin equivalent.

The callback must exactly match the external origin Spring sees. Set the two Google environment variables and restart. The frontend shows Google sign-in only when the backend reports it configured. Test a new provider subject and an existing email collision; the latter must not silently link accounts.

## Database and migrations

Flyway runs at backend startup. Do not enable Hibernate DDL auto-update. Apply migration changes through a new versioned SQL file once V1 has run. Back up and test restore before production migration. Use a separate migration role if runtime DDL privileges are inappropriate.

Local integration tests use the `cft_test` schema. Never point tests at production. Local `setup-local.ps1` creates a separate cluster and random secrets; it does not discover or reuse another database's credentials.

## Packaging and verification

- Frontend: `npm ci && npm run build`; serve `dist/` with SPA fallback and HTTPS.
- Backend: Java 21 / Maven 3.9+, `mvn -s maven-settings.xml package`; run the executable JAR.
- Python: install `requirements-lock.txt`, train with `python -m training.train`, run Uvicorn on private networking.
- Health: backend `/actuator/health`, Python `/health`, frontend `/`.
- Boot and browser tests must run with network access to local sockets.

Container tags constrain runtime families; pin tested image digests in a production release after building/scanning them in the deployment environment. Docker could not be executed in the initial session because its engine was stopped. The local Java runtime is the installed JDK 21.0.3; use a current patched Java 21 runtime for deployment.

## Troubleshooting

- **401 after restart/reset**: sign in again. In-memory sessions intentionally do not survive a backend restart.
- **403 on mutation**: refresh CSRF and include cookies; ensure the exact origin is allowed. Never disable CSRF.
- **Mail seems absent**: development emails are `.eml` files in the configured spool. Production SMTP has not been confirmed until tested with the real provider.
- **Historical baseline displayed**: normal for real businesses. Demo requires a valid trained artifact and healthy private ML service.
- **Stale forecast**: refresh after record/settings changes or the business date advances. Existing scenarios retain their original baseline.
- **Duplicate occurrence conflict**: edit the existing rule/date replacement instead of adding a second.
- **Ports already used**: stop prior project processes. Startup scripts refuse to replace unknown listeners.
- **PowerShell/space-containing paths**: use the repository's scripts under PowerShell 7 and quote paths.
- **Python tests stall in restricted Windows environments**: async tests need loopback sockets; grant the test process network permission.
