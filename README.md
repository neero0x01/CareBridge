# CareBridge

Multi-tenant clinical workflow platform for **synthetic demo data** only.

**Not a medical device. Not real PHI. Not production clinical software.**

## Stack

| Layer | Tech |
| ----- | ---- |
| API | Java 21, Spring Boot 3.3, Flyway, PostgreSQL 16 |
| Web | Next.js (App Router), TypeScript, Tailwind |
| Ops | Docker Compose, GitHub Actions |

Layout: `services/api`, `apps/web`.

## Quick start (Compose)

```bash
cp .env.example .env
docker compose up --build
```

| Service | URL / port |
| ------- | ---------- |
| Web shell | http://localhost:3000 |
| Login | http://localhost:3000/login |
| Register tenant (demo/local) | http://localhost:3000/register |
| API health | http://localhost:8080/actuator/health |
| Postgres | localhost:5432 (`carebridge` / `carebridge` / `carebridge`) |

## Local development (without full Compose)

### Prerequisites

- Java 21, Maven 3.9+
- Node 22+, npm
- Postgres 16 (or `docker compose up db`)

### API

```bash
cd services/api
# Point at local Postgres (defaults match .env.example)
mvn spring-boot:run
```

```bash
mvn test   # includes Testcontainers health + Flyway IT
```

### Web

```bash
cd apps/web
npm install
npm run dev   # http://localhost:3000
```

## Verify builds

```bash
# API
(cd services/api && mvn -B verify)

# Web
(cd apps/web && npm ci && npm run build)
```

## Auth (M1)

- `POST /api/v1/auth/register-tenant` — create Tenant + first ORG_ADMIN (disabled when `REGISTER_TENANT_ENABLED=false` or `prod` profile)
- `POST /api/v1/auth/login` — `tenantSlug` + email + password → JWT access token (~15m)
- `GET /api/v1/me` — current User + Tenant (Bearer token)

JWT claims: `sub`, `tenant_id`, `role`, `email`. Passwords: BCrypt (cost 10).

## Status

**M1 auth** — register tenant, login, `/me`, portal login/register pages. Cases, invite, refresh rotation, audit, and webhooks land in later tickets.

See `CONTEXT.md` for domain language and Linear project **CareBridge** for tickets.
