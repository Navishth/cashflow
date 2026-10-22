# API contracts

Base path: `/api/v1`. JSON requests/responses, ISO `YYYY-MM-DD` dates, UTC timestamp strings, UUID resource IDs. Monetary responses are decimal JSON numbers; financial truth stays on the server.

## Session bootstrap

1. `GET /auth/csrf` returns `{ "token": "...", "headerName": "X-CSRF-TOKEN" }`.
2. Include credentials/cookies and that header on every POST, PATCH, and DELETE.
3. Register/verify, then `POST /auth/login`.
4. Fetch a fresh CSRF token after login, logout, or a CSRF rejection.
5. `GET /auth/me` hydrates the session; `POST /auth/logout` invalidates it and returns 204.

CSRF is session-backed with Spring Security's default XOR masking. Tokens stay in frontend memory. The session cookie is HttpOnly. Do not reuse the pre-login CSRF token.

| Endpoint | Input / behavior |
| --- | --- |
| `POST /auth/register` | `email`, `password` (12+ chars, max 72 UTF-8 bytes), `displayName` |
| `POST /auth/verify-email` | `token`; one-use, 24-hour expiry |
| `POST /auth/login` | `email`, `password`; verified users only |
| `GET /auth/me` | `id`, `email`, `displayName`, `verified` |
| `POST /auth/forgot-password` | `email`; generic response |
| `POST /auth/reset-password` | `token`, `password`; 30-minute expiry; invalidates existing sessions |
| `POST /auth/resend-verification` | `email`; generic response |
| `GET /auth/options` | Whether Google sign-in is configured |

Google starts at `/oauth2/authorization/google`, with callback `/login/oauth2/code/google` (outside `/api/v1`).

## Business, flows, and recurrence

`POST /businesses`, `GET /businesses/current`, `PATCH /businesses/current` use name, currency (`INR`), timezone, openingDate, openingBalance, reserve, and demo. One business per account. Demo is immutable after creation. The view includes currentBalance, today, and revision.

`GET /cash-flows?from=&to=&type=&category=&status=&page=0&size=20` returns `{items,page,size,totalElements,totalPages}`. Size is 1–100. Category is an exact match.

`GET /cash-flows/{id}`, `POST /cash-flows`, `PATCH /cash-flows/{id}`, `DELETE /cash-flows/{id}`.

```json
{
  "type": "INFLOW",
  "amount": 60000.00,
  "description": "Client milestone",
  "category": "Client payments",
  "counterparty": "Example customer",
  "expectedDate": "2026-10-25",
  "status": "PLANNED",
  "source": "SCHEDULED",
  "recurringRuleId": null,
  "occurrenceDate": null
}
```

Statuses: PLANNED, RECEIVED, PAID, CANCELLED. Source: SCHEDULED or UNPLANNED.

`GET/POST /recurring-rules`, `PATCH/DELETE /recurring-rules/{id}`. Fields: type, amount, description, category, frequency (WEEKLY/MONTHLY), startDate, nullable endDate, active. DELETE deactivates. See recurrence rules in the architecture document.

PATCH endpoints accept the full editable DTO, not a JSON Merge Patch. Client-supplied owner/business IDs never determine ownership. A foreign resource returns 404.

## Forecasts

`POST /forecasts/generate` always produces 30 days. No horizon parameter is used. `GET /forecasts/latest` and `GET /forecasts/{id}` retrieve saved runs.

Response fields include id, startDate, endDate, openingBalance, reserve, modelName, modelVersion, generatedAt, limitations, events, points, summary, and stale.

Each point has date, predictedUnplannedNet, scheduledInflows, scheduledOutflows, closingBalance. Summary has minimumBalance, minimumDate, reserveBreachDate (nullable), finalBalance.

## Decision Replay

`GET /scenarios?page=0` (20 per page), `GET /scenarios/{id}`, `POST /scenarios`, `PATCH /scenarios/{id}`, `DELETE /scenarios/{id}`.

```json
{
  "name": "Client pays late",
  "description": "Test a receipt arriving after the forecast horizon",
  "baselineId": "<owned forecast UUID>",
  "changes": [
    {
      "kind": "RESCHEDULE",
      "targetEventId": "<event ID from that forecast>",
      "newDate": "2026-12-01"
    }
  ]
}
```

Other kinds: `AMOUNT_CHANGE` with positive `amount` (replacement magnitude), `ADD_CASH_FLOW` with `newDate`, `amount`, `type`, and optional description, or `REMOVE_CASH_FLOW` with targetEventId. At most 20 changes.

`POST /scenarios/{id}/simulate` persists and returns a comparison; `GET /scenarios/{id}/compare` reopens it. Editing clears old simulation points until rerun. Comparison includes both summaries, baseline and scenario points, dated deltas, minimumBalanceChange, finalBalanceChange, newerBaselineAvailable, and an explanation.

## Errors and limits

Errors use status, message, fieldErrors; controller errors also include timestamp and path. 400 invalid input, 401 unauthenticated, 403 CSRF/CORS, 404 missing/inaccessible, 409 conflict/unsimulated scenario, 413 oversized body, 429 rate limit, 503 mail unavailable. Internal exception text is not returned.

Backend JSON bodies: 64 KB. Private inference: 128 KB, at most 730 history days, maximum 30-day horizon, consecutive dates ending the day before start. Internal key required on `/predict`.

Private Python request uses schemaVersion `"1"`, history `[{"date":"...","netFlow":123.45}]`, startDate, horizonDays, allowSyntheticModel. Response uses predictions in the same point shape, modelName, modelVersion, limitations. It does not return cash balances; Java calculates them.
