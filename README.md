# LedgerFlow

[![CI](https://github.com/labheshwar/ledgerflow/actions/workflows/ci.yml/badge.svg)](https://github.com/labheshwar/ledgerflow/actions/workflows/ci.yml)

A double-entry bookkeeping engine — the same accounting model real banks and payment
processors use, where every transaction is recorded as a balanced pair of entries (a debit
somewhere and a matching credit somewhere else), so the books can never silently drift out of
balance. It's a single Spring Boot service built around one core invariant: **for any
transaction, the sum of its debits must equal the sum of its credits**, enforced in code
before anything is written to the database.

## Why this exists

Ledgers sit at the center of every payment platform, but most day-to-day backend work around
them (onboarding, terminals, payment orchestration) treats the ledger as a black box you write
to, not something you build yourself. LedgerFlow opens that box: it implements the four things
that make a ledger trustworthy under real-world conditions — balanced postings, safe retries,
a reconciliation process against an external source of truth, and a permanent audit trail.

## Core features

- **Balanced posting** — every transaction is submitted as a set of entries; the service
  rejects anything where debits and credits don't sum to zero, inside a single database
  transaction, so a failure midway never leaves a half-posted entry behind.
- **Idempotent by design** — every posting carries a client-supplied idempotency key. A
  retried request (client timeout, dropped connection) is recognized and returns the original
  result instead of posting twice — including the case where two requests with the same key
  race each other.
- **Cached balance reads** — account balances are cached in Redis (cache-aside), invalidated
  the instant a posting touches that account, so the fast read path never risks serving stale
  data.
- **Asynchronous reconciliation** — triggering a reconciliation queues a message on RabbitMQ
  and returns immediately; a separate worker compares the ledger against a simulated external
  statement feed, records per-account matches/mismatches, and marks the batch complete or
  failed, with retry and dead-letter handling if the worker dies mid-run.
- **Concurrency safety** — every account carries an optimistic-lock version column, so two
  postings racing to update the same account can't silently clobber each other's balance.
- **Append-only audit trail** — every posting and reconciliation result appends a row to an
  audit log with before/after snapshots. Nothing is ever overwritten; the database itself
  rejects `UPDATE`/`DELETE` on that table.

## Architecture

```mermaid
flowchart LR
    Client([Client])

    subgraph app [LedgerFlow]
        API["REST API<br/>(JWT auth)"]
        Posting[PostingService]
        Balance[BalanceService]
        Reconcile[ReconciliationService]
    end

    Postgres[(PostgreSQL)]
    Redis[(Redis)]
    RabbitMQ{{RabbitMQ}}

    Client -->|"POST /transactions<br/>GET /accounts/:id"| API
    Client -->|POST /reconciliation/trigger| API

    API --> Posting --> Postgres
    API --> Balance
    Balance <-->|cache-aside| Redis
    Balance --> Postgres
    Posting -. evict on commit .-> Redis

    API -->|publish| RabbitMQ -->|consume| Reconcile --> Postgres
```

Two paths through one service: a **synchronous** path (JWT-authenticated REST → service layer
→ JPA → PostgreSQL, with Redis alongside for balance caching) for posting and reading, and an
**asynchronous** path (service → RabbitMQ → a dedicated worker → PostgreSQL) for reconciliation,
which shouldn't block a caller. JWT auth carries a role claim distinguishing `ADMIN` (can post,
can trigger reconciliation) from `VIEWER` (read-only).

**Stack**: Java 17, Spring Boot 3.5, PostgreSQL + Spring Data JPA, Flyway (versioned schema
migrations), Redis, RabbitMQ, JWT (jjwt), Docker Compose, JUnit + Mockito for unit tests,
Testcontainers for integration tests against real Postgres/Redis/RabbitMQ.

## Getting started

```bash
git clone https://github.com/labheshwar/ledgerflow.git
cd ledgerflow
cp .env.example .env
docker compose up -d --build
```

This brings up Postgres, Redis, RabbitMQ, the app, and the web frontend, waits for each
dependency to be healthy before starting the next, and applies the Flyway migrations
(including two seeded demo users and three demo accounts) on first boot. The API is then
available at `http://localhost:8080`, and the web UI at `http://localhost:8081`.

### Demo credentials

| Username | Password    | Role   |
|----------|-------------|--------|
| `admin`  | `admin123`  | ADMIN  |
| `viewer` | `viewer123` | VIEWER |

### Demo accounts

| ID | Name                 |
|----|----------------------|
| 1  | Cash                 |
| 2  | Accounts Receivable  |
| 3  | Revenue              |

## API walkthrough

Log in and grab a token:

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' \
  | sed -E 's/.*"token":"([^"]+)".*/\1/')
```

Post a balanced transaction (debit Cash, credit Revenue):

```bash
curl -s -X POST http://localhost:8080/transactions \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{
    "idempotencyKey": "demo-1",
    "description": "cash sale",
    "entries": [
      {"accountId": 1, "entryType": "DEBIT",  "amount": 100.00},
      {"accountId": 3, "entryType": "CREDIT", "amount": 100.00}
    ]
  }'
```

Retrying the exact same request (same `idempotencyKey`) returns the original transaction
instead of posting again. An unbalanced request (debits ≠ credits) gets a `400` instead.

Read the balance back (served from Redis after the first read):

```bash
curl -s http://localhost:8080/accounts/1/balance -H "Authorization: Bearer $TOKEN"
```

Trigger a reconciliation and check its status once it completes:

```bash
BATCH_ID=$(curl -s -X POST http://localhost:8080/reconciliation/trigger \
  -H "Authorization: Bearer $TOKEN" | sed -E 's/.*"id":([0-9]+).*/\1/')

curl -s http://localhost:8080/reconciliation/$BATCH_ID -H "Authorization: Bearer $TOKEN"
```

The trigger call returns immediately (`202 Accepted`, status `PENDING`); the batch flips to
`COMPLETED` shortly after, once the reconciliation worker has consumed the message and recorded
a `MATCHED`/`MISMATCHED` result per account.

### Listing, paging and filtering

Every list endpoint (`/accounts`, `/transactions`, `/reconciliation`, `/audit-log`) is paged and
returns the same envelope:

```json
{ "content": [], "page": 0, "size": 25, "totalElements": 0, "totalPages": 0 }
```

They accept `page`, `size` and `sort` (`sort=balance,desc`), plus per-resource filters — `q` for
a case-insensitive substring search, and `type` on accounts, `entityType` on the audit log,
`status` on reconciliation:

```bash
curl -s "http://localhost:8080/accounts?type=ASSET&sort=balance,desc&size=5" \
  -H "Authorization: Bearer $TOKEN"
```

Sortable fields are whitelisted per endpoint; `?sort=` on anything else returns `400` with code
`INVALID_SORT` rather than quietly exposing an unindexed or unintended column.

### Errors

Error responses carry a stable, machine-readable `code` alongside the human-readable `message`:

```json
{ "timestamp": "...", "status": 400, "error": "Bad Request",
  "code": "UNBALANCED_TRANSACTION", "message": "..." }
```

Current codes: `UNBALANCED_TRANSACTION`, `ACCOUNT_NOT_FOUND`, `NOT_FOUND`, `INVALID_SORT`,
`INVALID_PARAMETER`, `VALIDATION_FAILED`, `INVALID_CREDENTIALS`, `UNAUTHENTICATED`, `FORBIDDEN`,
`INTERNAL_ERROR`.

### OpenAPI

The generated spec is at `/v3/api-docs` and Swagger UI at `/swagger-ui.html`, both unauthenticated
so you can read the contract before you have a token.

## Frontend

A Vue 3 + Vite + TypeScript single-page app in [`frontend/`](frontend), styled after the
original design mockups. It's a thin client over the API above — every page reads real data
from the endpoints already described (accounts, transactions, reconciliation, audit log) and
nothing is mocked. `ADMIN` sees posting/reconciliation controls; `VIEWER` gets the same pages
read-only.

Deliberately left out, matching gaps in the API itself: transaction reversal, CSV export, and
per-discrepancy resolution — the reconciliation model here compares whole account balances, not
individual bank-line items.

In Docker, it's served by nginx at `http://localhost:8081`, with `/api/*` proxied to the `app`
service (no CORS configuration needed since the browser only ever talks to one origin). For
local development with hot reload against the backend from `docker compose`:

```bash
cd frontend
npm install
npm run dev
```

This opens on `http://localhost:5173`, with Vite's dev server proxying `/api/*` to
`http://localhost:8080` (see `vite.config.ts`). Log in with either demo account from the table
above.

## Running tests

```bash
mvn test          # unit tests only (JUnit + Mockito, no external dependencies)
mvn verify         # unit + integration tests (spins up real Postgres/Redis/RabbitMQ via Testcontainers)
```

## Known limitations

This project is honest about where it's simplified, rather than hiding the gaps:

- **No distributed tracing** across the async reconciliation path yet.
- **Cached running total, not full event-sourcing** — account balances are a cached running
  total updated on each posting, rather than always derived by summing entries. A stricter
  design would recompute balances purely from the entry log.
- **No currency conversion** — currency is stored per account, but converting between
  currencies isn't implemented.
- **Single-node Redis and RabbitMQ** — no HA/clustering setup for either.

These are the natural next steps, not oversights being hidden.

## License

[MIT](LICENSE)
