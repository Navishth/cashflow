# CashFlow

A full-stack cash-flow forecasting and Decision Replay application for small businesses. Records and financial calculations live in Spring Boot/PostgreSQL. React calls only the Java API; the private Python service estimates unplanned movement.

## Run on this Windows machine

Use **PowerShell 7** in `N:\Personal Project\Fintech`. The setup script uses the existing PostgreSQL 17 binaries and creates its own cluster on port **55432**. It does not alter the machine's other PostgreSQL installations.

```powershell
.\scripts\setup-local.ps1
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r ml-service\requirements-lock.txt
Push-Location ml-service
..\.venv\Scripts\python.exe -m training.train
Pop-Location
.\scripts\frontend.ps1 -NpmArgs @('ci')
.\scripts\frontend.ps1 -NpmArgs @('run','build')
.\scripts\backend.ps1 -MavenArgs @('-B','-ntp','package')
.\scripts\start-local.ps1
```

Open **[CashFlow](http://localhost:5173)**. Register an account, open its message in `.local/mail/`, follow the verification link, and sign in. Messages are **local files**, not emails delivered to a provider. Select “Explore with a sample business” during onboarding for synthetic records and the trained demonstration model.

Local settings and random service/database credentials are generated in ignored `.local/environment.json`. No public demo account or default password is shipped. Use Node 22.12+ LTS or 24+; the scripts use this machine's bundled Node 24 if the system Node is unsupported. The backend uses the included Maven 3.9.11 Wrapper. Set `JAVA_HOME` to Java 21 on another machine; this machine can use IntelliJ's bundled JDK. Local Python 3.11 is supported and tested; Docker targets Python 3.12.

```powershell
# Stop only the processes recorded by start-local.ps1:
.\scripts\stop-local.ps1
# Also stop this project's isolated PostgreSQL cluster:
.\scripts\stop-local.ps1 -Database
```

The background startup script serves the compiled frontend and packaged Java JAR. This is a local preview; use the Nginx deployment configuration for hosting. Rebuild after source changes before restarting it.

For foreground development with live frontend updates, use three terminals after setup:

```powershell
.\scripts\backend.ps1
.\scripts\ml.ps1
# Third terminal:
.\scripts\frontend.ps1
```

## What the application does

- Registration, local/SMTP verification, login, secure server sessions, logout, recovery, and configurable Google OIDC.
- One owned business per account, opening balance/date, INR, timezone, and minimum reserve.
- Cash-flow CRUD, pagination and filters, weekly/monthly recurring commitments, cancellation and settlement replacement.
- Persisted 30-day forecasts with known scheduled events, unplanned estimates, model provenance, minima, and reserve-breach dates.
- Saved Decision Replay scenarios: move, resize, add, or exclude planned transactions; simulate and compare without changing real records.
- Clearly isolated synthetic sample businesses, responsive views, validation, loading/error states, and accessible controls.

The current cash balance is the opening balance plus settled movements **on or after the opening date through today** in the business timezone. A forecast begins **tomorrow** and covers 30 calendar days. Overdue planned items are excluded with a warning until the user reschedules or settles them.

The synthetic model is only used for demo businesses. Real businesses use a same-weekday baseline from their own explicitly unplanned settled history; fewer than 28 days use a scheduled-only forecast. Forecasts are estimates, not guarantees. See [financial semantics](docs/ARCHITECTURE.md) and [measured model results](docs/MODEL_EVALUATION.md).

## Verification

```powershell
.\scripts\backend.ps1 -MavenArgs @('-B','-ntp','test')
Push-Location ml-service
..\.venv\Scripts\python.exe -m pytest -q
Pop-Location
.\scripts\frontend.ps1 -NpmArgs @('run','build')
.\scripts\frontend.ps1 -NpmArgs @('test')
# With all services running; creates only synthetic test records:
.\.venv\Scripts\python.exe scripts\smoke.py
```

Backend tests use a separate `cft_test` PostgreSQL schema. They do not clean or drop the application's `public` schema. Windows test tools need local socket permissions. Do not run these tests against a production database.

See [implementation evidence and remaining gaps](docs/IMPLEMENTATION_STATUS.md).

To repeat the Java runtime advisory check (requires network access):

```powershell
.\scripts\backend.ps1 -MavenArgs @('-B','-ntp','package','dependency:list','-DincludeScope=runtime','-DoutputFile=../.local/maven-dependencies.txt')
.\.venv\Scripts\python.exe scripts\audit-java.py
```

The scan exits nonzero when advisories remain, including conditional findings assessed in [security notes](docs/SECURITY.md). It does not silently suppress them.

## Docker and deployment

Copy `.env.example` to `.env`, replace both placeholder secrets, then run:

```sh
docker compose up --build
```

Frontend: `http://localhost:5173`. Only the frontend binds a host port; PostgreSQL and Python remain on an internal network. Database data and the development mail spool use named volumes. For development mail:

```sh
docker compose exec backend sh -c 'ls /app/mail'
docker compose exec backend sh -c 'cat /app/mail/<message-id>.eml'
```

For HTTPS, managed PostgreSQL, SMTP, Google, and separate hosting, follow [deployment configuration](docs/DEPLOYMENT.md). Docker image execution must be verified on a machine with a running Docker engine. No cloud deployment has been performed.

## Repository

| Folder | Responsibility |
| --- | --- |
| `frontend/` | React, strict TypeScript, Vite, Tailwind, shadcn-style Button, Recharts, Query, Hook Form, Zod |
| `backend/` | Java 21, Spring Boot 3.5.16, Security, JPA, Flyway, PostgreSQL, OpenAPI |
| `ml-service/` | FastAPI, reproducible synthetic generation, leak-safe features, training, evaluation, inference |
| `scripts/` | Local environment, startup/shutdown, live integration smoke test |
| `docs/` | Architecture, API, security, evaluation, status, deployment |

API reference: sign in first, then open [Swagger UI](http://127.0.0.1:8080/swagger-ui/index.html). For a same-origin dev login, visit the backend CSRF/API routes through the Vite proxy or use a credentialed API client. Swagger mutations require the CSRF header; see [API contracts](docs/API.md).
