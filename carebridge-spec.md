# CareBridge — Product & Technical Spec

**Working title:** CareBridge  
**Tagline:** Multi-tenant clinical workflow platform (demo/synthetic data only)  
**Difficulty:** Intermediate → Advanced  
**Primary stack:** Java 21 + Spring Boot 3 + PostgreSQL + Next.js  
**Audience:** Portfolio / GitHub / UK full-stack & backend interviews  

**Important:** Synthetic patients only. Not a medical device, not real PHI, not production clinical software. State that in the README.

---

## 1. Problem

Clinics need a simple system where:

1. Staff create and update **cases** (e.g. referral review, prescription check, discharge summary review).  
2. Cases move through a **workflow** (To do → In review → Needs info → Approved / Rejected).  
3. Access is **role-based** and **tenant-isolated**.  
4. Every sensitive change is **audited**.  
5. External systems can push events via **webhooks** (e.g. “lab result ready”) without double-processing.

You will build a **slice** of that world that looks serious in interviews: multi-tenancy, RBAC, audit, outbox, OpenAPI, tests, Docker.

---

## 2. Goals

| ID | Goal |
|----|------|
| G1 | Multi-tenant isolation (no cross-tenant reads/writes) |
| G2 | JWT auth + RBAC for 4 roles |
| G3 | Case lifecycle with transitions + validation |
| G4 | Immutable audit log for case + membership changes |
| G5 | Async processing via outbox + worker (at-least-once) |
| G6 | Webhook ingest with signature verify + idempotency |
| G7 | Next.js portal: login, board, case detail, admin users |
| G8 | Docker Compose one-command demo |
| G9 | CI: build, test, lint on PR |

## 3. Non-goals (v1)

- Real FHIR server, HL7 feeds, or EHR certification  
- Real payments, e-prescribing, or clinical decision support  
- Mobile apps  
- Full multi-region HA  
- SSO/SAML/OIDC (document as v2)  
- Real email/SMS (log only)

---

## 4. Personas & roles

| Role | Capabilities |
|------|----------------|
| **ORG_ADMIN** | Manage users, invite, view all cases, view audit |
| **CLINICIAN** | Create/update own cases, transition cases assigned to them or unassigned |
| **REVIEWER** | Claim cases, approve/reject/request info |
| **AUDITOR** | Read-only cases + full audit log for tenant |

One user belongs to **exactly one tenant** in v1 (simpler).  
v2: multi-membership.

---

## 5. User stories (MVP)

### Auth & tenancy
1. As a new org, I can **register a tenant** (clinic name, slug) and first **ORG_ADMIN**.  
2. As admin, I can **invite** users by email + role (create user with temp password or magic token).  
3. As any user, I can **login** and receive JWT (access + refresh).  
4. As any user, I **cannot** see another tenant’s data.

### Cases & workflow
5. As clinician, I can **create a case** (title, type, priority, patient display name/synthetic ID, notes).  
6. As clinician/reviewer, I can **list cases** filtered by status, assignee, priority.  
7. As reviewer, I can **claim** an unassigned case.  
8. As assignee/reviewer, I can **transition** status with an optional comment.  
9. As admin/auditor, I can **view case history** (transitions + comments).

### Audit & webhooks
10. Every create/update/transition/user-role change writes an **audit entry**.  
11. System accepts **signed webhooks** `lab.result.ready` that create a case or comment (idempotent by `event_id`).  
12. Outbox worker **publishes** domain events (e.g. `case.approved`) to a queue or log sink (console + optional RabbitMQ).

### Portal
13. Login page.  
14. Kanban board by status.  
15. Case detail: notes, transitions, audit snippet.  
16. Admin: user list + invite form.

---

## 6. Domain model

### Entities

```
Tenant
  id (UUID)
  name
  slug (unique)
  created_at

User
  id (UUID)
  tenant_id (FK)
  email (unique within tenant)
  password_hash
  full_name
  role (enum)
  active (bool)
  created_at

Case
  id (UUID)
  tenant_id (FK)
  case_number (human readable, per-tenant sequence e.g. CB-1042)
  title
  type (enum: REFERRAL | PRESCRIPTION_REVIEW | DISCHARGE | LAB_FOLLOWUP | OTHER)
  priority (enum: LOW | MEDIUM | HIGH | URGENT)
  status (enum: see workflow)
  patient_display_name (synthetic)
  patient_ref (synthetic string, e.g. PAT-001)
  description (text)
  created_by (user_id)
  assignee_id (nullable user_id)
  version (optimistic lock)
  created_at, updated_at

CaseComment
  id, case_id, tenant_id, author_id, body, created_at

CaseTransition
  id, case_id, tenant_id, from_status, to_status, actor_id, comment, created_at

AuditLog
  id, tenant_id, actor_id (nullable for system)
  action (string)
  entity_type, entity_id
  before_json, after_json (nullable)
  ip (optional)
  created_at

WebhookEvent
  id (UUID)  // client-supplied event_id for idempotency
  tenant_id
  type
  payload_json
  signature_valid
  processed_at
  status (RECEIVED | PROCESSED | FAILED)

OutboxMessage
  id, tenant_id
  aggregate_type, aggregate_id
  event_type
  payload_json
  created_at
  processed_at (nullable)
  attempts
```

### Workflow (state machine)

```
                    ┌──────────────┐
                    │   TO_DO      │
                    └──────┬───────┘
                           │ claim / assign
                           v
                    ┌──────────────┐
         ┌─────────│  IN_REVIEW   │─────────┐
         │         └──────┬───────┘         │
         │ request_info   │ approve         │ reject
         v                v                 v
  ┌──────────────┐  ┌──────────┐    ┌──────────┐
  │ NEEDS_INFO   │─►│ APPROVED │    │ REJECTED │
  └──────────────┘  └──────────┘    └──────────┘
         │ re-submit
         └──────────► IN_REVIEW
```

**Transition rules (enforce in domain service, not only UI):**

| From | To | Who |
|------|-----|-----|
| TO_DO | IN_REVIEW | REVIEWER (claim) or ORG_ADMIN (assign) |
| IN_REVIEW | NEEDS_INFO | REVIEWER, ORG_ADMIN |
| IN_REVIEW | APPROVED | REVIEWER, ORG_ADMIN |
| IN_REVIEW | REJECTED | REVIEWER, ORG_ADMIN |
| NEEDS_INFO | IN_REVIEW | CLINICIAN (creator/assignee), ORG_ADMIN |
| * | * | AUDITOR: never |

Illegal transitions → **409** with clear error code.

---

## 7. API design (REST)

Base: `/api/v1`  
Auth: `Authorization: Bearer <access_jwt>`  
Tenant: derived from JWT claims (`tenant_id`, `user_id`, `role`) — **never trust client tenant header alone** (optional `X-Tenant-Id` only if it matches JWT).

### Auth
```
POST /api/v1/auth/register-tenant
  body: { tenantName, slug, adminEmail, adminPassword, adminFullName }
  → 201 { tenant, user, tokens }

POST /api/v1/auth/login
  body: { tenantSlug, email, password }
  → 200 { accessToken, refreshToken, expiresIn }

POST /api/v1/auth/refresh
  body: { refreshToken }
  → 200 { accessToken, refreshToken }

GET  /api/v1/me
  → current user + tenant
```

### Users (ORG_ADMIN)
```
GET    /api/v1/users
POST   /api/v1/users          { email, fullName, role, temporaryPassword }
PATCH  /api/v1/users/{id}     { role?, active? }
```

### Cases
```
GET    /api/v1/cases?status=&assignee=&priority=&q=&page=&size=
POST   /api/v1/cases          { title, type, priority, patientDisplayName, patientRef, description }
GET    /api/v1/cases/{id}
PATCH  /api/v1/cases/{id}     { title?, description?, priority?, assigneeId? }  // version header/body
POST   /api/v1/cases/{id}/transitions  { toStatus, comment? }
GET    /api/v1/cases/{id}/transitions
POST   /api/v1/cases/{id}/comments     { body }
GET    /api/v1/cases/{id}/comments
```

### Audit (ORG_ADMIN, AUDITOR)
```
GET /api/v1/audit?entityType=&entityId=&from=&to=&page=
```

### Webhooks (per-tenant secret)
```
POST /api/v1/webhooks/inbound
  headers:
    X-CareBridge-Signature: sha256=<hmac>
    X-CareBridge-Event-Id: <uuid>
    X-CareBridge-Tenant: <slug>   // resolve tenant + secret
  body: { type, payload }
  → 202 { accepted: true } or 200 if already processed
```

Example event:
```json
{
  "type": "lab.result.ready",
  "payload": {
    "patientRef": "PAT-001",
    "testName": "HbA1c",
    "summary": "Synthetic result for demo"
  }
}
```
Handler: create `LAB_FOLLOWUP` case if none open for that patientRef, else add comment.

### Health
```
GET /actuator/health
GET /api/v1/public/version
```

**Errors (consistent):**
```json
{
  "code": "ILLEGAL_TRANSITION",
  "message": "Cannot move from APPROVED to TO_DO",
  "traceId": "..."
}
```

OpenAPI 3 via springdoc → `/v3/api-docs` + Swagger UI.

---

## 8. Multi-tenancy strategy (v1)

**Shared schema + `tenant_id` on every row.**

Rules:
1. Every repository query **must** filter `tenant_id = currentTenant()`.  
2. Hibernate/JPA: optional `@Filter` or explicit specs — prefer **explicit** in code for interview clarity.  
3. Integration tests: Tenant A cannot GET Tenant B’s case id (expect **404**, not 403 — avoid ID leakage).  
4. DB constraint: composite FKs where useful (`case_id`, `tenant_id`).

---

## 9. Security requirements

| Topic | Spec |
|-------|------|
| Passwords | BCrypt (cost ≥ 10) |
| JWT | Access 15m; Refresh 7d (rotate on use) |
| Claims | `sub`, `tenant_id`, `role`, `email` |
| Secrets | Webhook HMAC-SHA256 over raw body + event id |
| Headers | Security headers on API (or reverse proxy) |
| CORS | Only Next.js origin in env |
| Input | Bean Validation (`@NotBlank`, size limits) |
| Logs | Never log passwords or full JWT |

---

## 10. Architecture

```
┌─────────────┐     JWT      ┌──────────────────┐
│  Next.js    │ ───────────► │  Spring Boot API │
│  Portal     │              │  (carebridge-api)│
└─────────────┘              └────────┬─────────┘
                                      │
                    ┌─────────────────┼─────────────────┐
                    ▼                 ▼                 ▼
               PostgreSQL         Outbox poller      (optional)
               (primary)          ───────────────►  RabbitMQ/Redis
                                  worker process      consumers
```

**Modules (Java packages):**
```
com.carebridge
  config
  tenant          // TenantContext, filter
  security        // JWT, UserDetails
  identity        // User, Auth
  cases           // domain + service + controller
  audit
  webhooks
  outbox
  common          // errors, pagination
```

**Transactional outbox:**
1. Business transaction writes domain rows + `OutboxMessage`.  
2. Scheduled worker (`@Scheduled` or separate process) marks processed and publishes.  
3. At-least-once delivery; consumers must be idempotent (for demo, consumer = structured log + DB `processed_events` table).

---

## 11. Tech stack (locked for portfolio)

### Backend
- Java **21**  
- Spring Boot **3.3+**  
- Spring Security + OAuth2 Resource Server style JWT (or jjwt)  
- Spring Data JPA  
- PostgreSQL 16  
- Flyway migrations  
- springdoc-openapi  
- Testcontainers (Postgres)  
- MapStruct or manual mappers  

### Frontend
- Next.js 14+ (App Router)  
- TypeScript  
- Tailwind  
- React Query or SWR  
- Simple kanban (dnd optional — columns as lists is enough)

### Ops
- Docker + Docker Compose (`api`, `web`, `db`, optional `rabbitmq`)  
- GitHub Actions: `./mvnw verify` + `npm run build`  
- `.env.example` documented  

---

## 12. Frontend pages

| Route | Purpose |
|-------|---------|
| `/login` | tenant slug + email + password |
| `/register` | create tenant + admin (demo only; disable in “prod” profile) |
| `/board` | cases by status columns |
| `/cases/[id]` | detail, transition buttons, comments |
| `/admin/users` | list + invite (ORG_ADMIN) |
| `/admin/audit` | filterable audit table |

**UX notes:** loading/empty states; toast errors; disable illegal transitions in UI **and** enforce server-side.

---

## 13. Seed data

On startup (`ApplicationRunner` profile `demo`):

- Tenant `demo-clinic`  
- Users: admin@demo.local / clinician@ / reviewer@ / auditor@ (password in README)  
- 8–12 synthetic cases across statuses  
- Sample audit rows  

---

## 14. Testing strategy

| Layer | What |
|-------|------|
| Unit | State machine transitions; RBAC decision helper |
| Integration | Auth, case CRUD, cross-tenant isolation, webhook idempotency |
| Contract | OpenAPI generated or snapshot |
| E2E (optional) | Playwright: login → create case → transition |

**Must-have tests for “advanced” claim:**
1. Cross-tenant isolation  
2. Illegal transition rejected  
3. Same `event_id` webhook processed once  
4. Audit written on transition  

---

## 15. Repo structure

```
carebridge/
  README.md
  docker-compose.yml
  .github/workflows/ci.yml
  apps/
    web/                 # Next.js
  services/
    api/                 # Spring Boot
      src/main/java/...
      src/main/resources/db/migration/
      src/test/java/...
  docs/
    architecture.md      # one diagram + decisions
    api-examples.http    # REST Client file
```

---

## 16. README must include

1. Problem + disclaimer (synthetic data)  
2. Architecture diagram (mermaid)  
3. Quick start: `docker compose up`  
4. Demo accounts  
5. Key design decisions (tenant_id, outbox, 404 vs 403)  
6. Screenshots: board, case detail, audit  
7. “What I’d add next” (OIDC, FHIR, real queue)  
8. License MIT  

---

## 17. Milestones (build order)

### M0 — Skeleton (2–3 days)
- Spring Boot app + Flyway + health  
- Next.js app shell  
- Compose with Postgres  

### M1 — Auth & tenancy (4–5 days)
- Register tenant, login, JWT  
- TenantContext  
- Seed users  
- Isolation tests  

### M2 — Cases & workflow (5–7 days)
- Case CRUD + transitions  
- Optimistic locking  
- Board UI + detail UI  

### M3 — Audit + webhooks + outbox (4–5 days)
- Audit interceptor/service  
- Webhook HMAC + idempotency  
- Outbox worker  

### M4 — Polish (3–4 days)
- OpenAPI polish  
- Admin users + audit UI  
- CI + screenshots + README  
- One “chaos” demo script in README  

**Total:** ~4–6 weeks part-time.

---

## 18. Acceptance criteria (done = ship)

- [ ] `docker compose up` brings API + web + DB  
- [ ] Demo logins work for all 4 roles  
- [ ] Cannot access other tenant’s case by UUID  
- [ ] Workflow enforces table above  
- [ ] Audit shows transitions  
- [ ] Webhook twice with same event id → one effect  
- [ ] OpenAPI available  
- [ ] CI green  
- [ ] README with screenshots  

---

## 19. Interview talking points

1. **Why tenant_id not DB-per-tenant** — cost/complexity for v1; trade-offs.  
2. **Why 404 not 403** for cross-tenant — IDOR hygiene.  
3. **Outbox vs dual-write** — consistency.  
4. **Idempotent webhooks** — real B2B requirement.  
5. **Optimistic locking** — concurrent reviewers.  
6. **Honest healthcare scope** — synthetic only; inspiration from FHIR, not full compliance.

---

## 20. Stretch (after v1)

- OIDC (Keycloak in Compose)  
- FHIR R4 Patient read-only facade  
- OpenTelemetry + Grafana  
- Spring Authorization Server  
- Kubernetes manifests  
- Dual implementation note: “same domain in NestJS” comparison post  

---

## 21. Naming & branding

- Repo: `carebridge` or `carebridge-platform`  
- Package: `com.carebridge`  
- Default port: API `8080`, web `3000`  
- JWT issuer: `carebridge-local`  
