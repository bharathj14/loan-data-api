# Full-Spec Gap Remediation — Design Document

**Date:** 2026-10-13  
**Status:** Approved (autonomous execution)

---

## Goal

Bring the existing single-module Spring Boot POC up to the full production-reference spec the banking client will review. All 22 gaps identified in the audit are being fixed.

---

## Downstream API Contract (Placeholder)

- **Service name:** Loan Servicing API
- **GET** `/v1/loans/{loanNumber}/payments`  
  Response: `loanNumber`, `totalPayments`, `nextPaymentDueDate`, `nextPaymentAmount`,  
  `payments[]` → `{paymentNumber, paymentDate, amount, status, principalPaid, interestPaid, remainingBalance}`
- **POST** `/v1/loans/{loanNumber}/notes`  
  Request: `{noteType: enum(GENERAL|PAYMENT|LEGAL), noteText, createdBy}`  
  Response: `{noteId, loanNumber, noteType, noteText, createdBy, createdAt}`
- **Auth:** none for POC; Google ID token placeholder for gcp profile (to confirm with client)
- **Our endpoints:** `GET /api/v1/loans/{loanNumber}/payments` (proxy), `POST /api/v1/loans/{loanNumber}/notes` (proxy)

---

## Architecture

```
loan-data-api/               ← repo root
  loan-data-api/             ← :loan-data-api module (Cloud Run deployable)
  downstream-stub/           ← :downstream-stub module (local only, never deployed)
  gateway/                   ← API Gateway OpenAPI config (infra repo in target)
  deploy/                    ← Cloud Run deploy docs (infra repo in target)
  CLAUDE.md
  settings.gradle            ← includes both modules
```

**Profile split:**
- `local` → plain Postgres + Bigtable emulator + downstream-stub on :8081
- `gcp` → AlloyDB connector + real Bigtable + real downstream base URL + JWT validation

---

## Implementation Phases

### Phase 0 — CLAUDE.md + multi-module restructure
- Create `CLAUDE.md` at repo root with engineering standards and build commands
- Convert `settings.gradle` to include `:loan-data-api` and `:downstream-stub`
- Move existing source into `loan-data-api/` submodule directory
- Create `downstream-stub/` shell (minimal Spring Boot app, port 8081)
- Create root `build.gradle` with shared config (Spotless, JaCoCo, Java toolchain)
- Add TEMPORARY header comments to downstream-stub files and settings.gradle
- Create `deploy/README.md` and `gateway/README.md` with infra-repo notes
- `.\gradlew.bat check` must pass

### Phase 1 — Profile rename + AlloyDB URL + config records
- Rename `application-prod.yaml` → `application-gcp.yaml`
- Replace all `@Profile("prod")` → `@Profile("gcp")`
- Fix AlloyDB JDBC URL: `SocketFactory`, `alloydbInstanceName`, `alloydbEnableIAMAuth=true`, `alloydbIpType`
- Add `AlloyDb` nested record to `AppProperties` for gcp datasource values
- Add `Downstream` nested record to `AppProperties` for base-url, timeouts, retry
- Add `currentSchema=loan_data` to local JDBC URL
- `.\gradlew.bat check` must pass

### Phase 2 — Schema + Entities + Lombok
- Add Lombok to `loan-data-api/build.gradle`
- Add `CREATE SCHEMA IF NOT EXISTS loan_data` to `01_schema.sql`; prefix all `CREATE TABLE` with `loan_data.`
- Add `schema = "loan_data"` to all `@Table` annotations
- Convert `CustomerAccountRelationshipEntity` from `@IdClass` to `@EmbeddedId + @MapsId`
- Replace manual getters with Lombok `@Getter`; replace protected constructor with Lombok `@NoArgsConstructor(access = PROTECTED)`; add `@Setter` where appropriate
- Add `@OneToMany(mappedBy = "customer", fetch = LAZY)` to `CustomerEntity`
- Update seed data: `LN1000000001..LN1000000005`; add no-accounts customer, ended relationship, business customer, guarantor/authorized user
- `LocalBigtableSeeder` bean: `@Profile("local")`, creates `loans`/`details` if missing, seeds `LN1000000001..LN1000000005`
- `.\gradlew.bat check` must pass

### Phase 3 — Downstream client + downstream-stub
- `client/` package in `loan-data-api`: `LoanServicingClient` HTTP interface (`@GetExchange`, `@PostExchange`), `DownstreamClientConfig` (RestClient + HttpServiceProxyFactory), DTOs for payments/notes
- GET retry: bounded attempts + backoff on connection errors/5xx/429 (Spring Framework `@Retryable` if available, else Resilience4j — check resolved classpath first)
- POST: never retry; `Idempotency-Key` header (UUID, accept from caller if provided)
- `X-Correlation-Id` propagation to downstream
- Error mapping: 4xx → our 4xx (404→404), 5xx/timeout → 502/503
- `downstream-stub/`: realistic sample data for all loan numbers; `LN_ERROR_500` triggers 500 for testing
- `.\gradlew.bat check` must pass

### Phase 4 — New endpoints
- `LoanPaymentsController`: `GET /api/v1/loans/{loanNumber}/payments` — delegates to `LoanPaymentsService` → `LoanServicingClient`
- `LoanNotesController`: `POST /api/v1/loans/{loanNumber}/notes` — validates body, delegates to `LoanNotesService` → `LoanServicingClient`
- Full OpenAPI annotations including 400/404/502/503 error responses
- `.\gradlew.bat check` must pass

### Phase 5 — Security
- Add `spring-boot-starter-security` and `spring-boot-starter-oauth2-resource-server` to `loan-data-api/build.gradle`
- `security/` package: `SecurityConfig` class
  - gcp profile: stateless, JWT validation (issuer + audience from env), actuator health open, everything else requires bearer token
  - local profile: stateless, permit all (still sets security headers)
- Security headers: `X-Content-Type-Options`, `X-Frame-Options`, `Cache-Control`
- CORS off unless configured
- `.\gradlew.bat check` must pass

### Phase 6 — Log masking + .env.example + Testcontainers fix
- Add log masking utility: mask full names (first char + ***), amounts (redact), account/loan numbers (last 4 only)
- Apply masking in service log statements
- `.env.example` with all env vars and dummy values
- Add `.env` to `.gitignore`
- Change integration test `@Disabled` → `@Testcontainers(disabledWithoutDocker = true)`
- `.\gradlew.bat check` must pass

### Phase 7 — Tests for new code
- Unit tests: `LoanServicingClientTest` (MockRestServiceServer), `LoanPaymentsServiceTest`, `LoanNotesServiceTest`
- Controller slice tests: `LoanPaymentsControllerTest`, `LoanNotesControllerTest`
- Security tests: verify actuator open, API returns 401 without token (gcp mock), local permits all
- `.\gradlew.bat check` at ≥ 80% coverage must pass

### Phase 8 — Final docs + commit
- Update `CLAUDE.md`, `README.md`, `docs/DECISIONS.md` to reflect new endpoints and security
- PowerShell seed script alongside the existing bash one
- `.\gradlew.bat check` final run; git commit

---

## Key Decisions

| Decision | Rationale |
|----------|-----------|
| Fix all 22 gaps | Client-facing POC; quality is non-negotiable |
| Downstream contract is placeholder | Client hasn't defined it; realistic stub is better than no stub |
| Keep docker-compose.yml | Annotate as optional; `LocalBigtableSeeder` is the primary path |
| No virtual threads | Java 17; spec explicit |
| Fail-fast on missing downstream source | No partial data returned; 503 on any failure |
