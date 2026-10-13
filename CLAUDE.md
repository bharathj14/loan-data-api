# CLAUDE.md — Engineering Standards

## Build & Run Commands
- Check (lint + 80% line-coverage gate): `.\gradlew.bat :loan-data-api:check`
- Run loan-data-api (terminal): `$env:SPRING_PROFILES_ACTIVE='local'; .\gradlew.bat :loan-data-api:bootRun`
- Run loan-data-api (IntelliJ): add env var `SPRING_PROFILES_ACTIVE=local` to run config
- Run downstream-stub (terminal): `$env:SPRING_PROFILES_ACTIVE='local'; .\gradlew.bat :downstream-stub:bootRun`
- Tests only: `.\gradlew.bat :loan-data-api:test`
- Format: `.\gradlew.bat :loan-data-api:spotlessApply`

## Architecture
- Multi-module: `:loan-data-api` (deployable, Cloud Run) + `:downstream-stub` (local only, never deployed)
- Three data sources in loan-data-api: AlloyDB (JPA), Bigtable (gRPC), downstream REST
- Profiles: `local` (Postgres + Bigtable emulator + stub on :8081) | `gcp` (AlloyDB + real Bigtable + real downstream)
- gateway/ and deploy/ belong in the infrastructure repository in the target setup

## Engineering Standards
### Layering
- Packages: config, controller, service, repository.alloydb, repository.bigtable, client, model.dto, model.entity, mapper, exception, observability, security
- Controllers: HTTP + OpenAPI annotations only
- Services: business logic and orchestration
- Repositories/clients: I/O only — no business logic
- No JPA, Bigtable, or HTTP-client types above their layer
- Constructor injection only; final fields; no @Autowired/@Inject field injection; no static mutable state

### DTOs and Entities
- DTOs are Java records
- Entities: Lombok @Getter/@Setter/@NoArgsConstructor(access=PROTECTED) only — never @Data
- No equals/hashCode/toString over relations; equals/hashCode on ID only
- Composite keys: @EmbeddedId + @MapsId

### Data Access
- All associations LAZY; one query per endpoint via JOIN FETCH or @EntityGraph
- @Transactional(readOnly=true) on read services; open-in-view=false; ddl-auto=validate in gcp, ddl-auto=validate in local (schema created by 01_schema.sql via spring.sql.init)
- HikariCP configured from properties; one BigtableDataClient bean (closed on shutdown)
- Bigtable values: UTF-8 strings; decimals plain text; dates ISO yyyy-MM-dd; mapper tolerates nulls

### HTTP Client
- Spring RestClient + HTTP interface (@GetExchange/@PostExchange). No RestTemplate, no OpenFeign.
- GET: retry on connection errors/5xx/429 via spring-retry RetryTemplate interceptor (wired in DownstreamClientConfig)
- POST: never retry; Idempotency-Key header (UUID)
- Propagate X-Correlation-Id to downstream
- Map 4xx→our 4xx, 5xx/timeout→502/503; never forward downstream bodies verbatim

### API
- Base path /api/v1; Bean Validation on all inputs
- RFC 7807 ProblemDetail from one @RestControllerAdvice; no stack traces in responses
- Pagination: page/size, max 100

### Security
- Stateless. gcp: JWT OAuth2 Resource Server (issuer/audience from env vars OAUTH2_ISSUER_URI, OAUTH2_AUDIENCE). local: permit all but still stateless.
- Actuator health open; everything else authenticated in gcp

### Observability
- X-Correlation-Id filter (MDC); JSON structured logs
- Mask: never log full names/amounts/account numbers/loan numbers — last 4 chars only

### Code Style
- Every class file begins with a mandatory one-line header comment stating purpose and layer (this is always required, regardless of the rule below)
- Within method bodies: add inline comments only when the WHY is non-obvious — not for what the code does
- No dead code; no commented-out code; no System.out
