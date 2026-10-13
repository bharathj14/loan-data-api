# Loan Data API POC — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use godmode:task-runner to implement this plan task-by-task.

**Goal:** Build a production-grade read-only REST API on Spring Boot 4.1.1 / Java 17 exposing loan details from Cloud Bigtable and relationship accounts from AlloyDB, deployable on GCP Cloud Run behind API Gateway.

**Architecture:** Two REST endpoints backed by two distinct stores (Bigtable for loans, AlloyDB/PostgreSQL for accounts). Service layer is the only place that knows both stores exist. The relationship-accounts endpoint combines both stores via a bounded parallel executor when `includeLoanDetails=true`. All HTTP concerns stay in controllers; all data-access concerns stay in repositories.

**Tech Stack:** Spring Boot 4.1.1, Java 17, Google Cloud Bigtable (gRPC), AlloyDB via JDBC connector, Spring Data JPA, HikariCP, springdoc-openapi 3.0.3, Testcontainers (Docker required for integration tests), Jib, Spotless/google-java-format, JaCoCo.

**Docker note:** Docker is not available on this machine. Integration tests are stubbed with `@Disabled("REQUIRES_DOCKER")`. Unit tests and `@WebMvcTest` slices must achieve ≥ 80% line coverage for `./gradlew check` to pass.

---

## Phase 1 — Gradle Setup

**Files:** `build.gradle`

- Add plugins: `com.google.cloud.tools.jib:3.5.2`, `com.diffplug.spotless:7.0.4`, `jacoco`
- Import Google Cloud BOM (`com.google.cloud:libraries-bom:26.86.0`) via `dependencyManagement`
- Add deps: `google-cloud-bigtable`, `alloydb-jdbc-connector:1.3.2` (runtimeOnly prod), `springdoc-openapi-starter-webmvc-ui:3.0.3`
- Add test deps: `org.testcontainers:gcloud` (version managed by Spring Boot BOM)
- Configure Spotless with `googleJavaFormat('1.22.0')`
- Configure JaCoCo with 80% line-coverage gate; wire into `check`
- Configure Jib: base `eclipse-temurin:17-jre`, user `1000:1000`, `-XX:MaxRAMPercentage=75`, port 8080
- Verify: `./gradlew build` green; commit

## Phase 2 — Configuration + Clients

**Files:** `AppProperties.java`, `BigtableConfig.java`, `AlloyDbDataSourceConfig.java`, `ThreadPoolConfig.java`, `application.yaml`, `application-local.yaml`, `application-prod.yaml`

- `AppProperties` record with nested `Bigtable` and `Executor` records; `@Validated`, fail-fast on missing values
- `BigtableDataClient` bean scoped to `@Bean(destroyMethod="close")`; emulator branch for local profile
- `DataSource` bean using HikariCP; AlloyDB socket factory in prod, plain PostgreSQL in local/test
- Bounded `ThreadPoolTaskExecutor` bean with `MdcTaskDecorator`
- Verify: `./gradlew build` green; commit

## Phase 3 — Loan Endpoint

**Files:** `BigtableRepository`, `LoanService`, `LoanController`, `LoanMapper`, `LoanDetailResponse`, `LoanNotFoundException`, `DatastoreException`

- Repository reads single row from table `loans`, column family `details`, latest cell per column
- Service calls repository, maps result; throws `LoanNotFoundException` for null row
- Controller: `GET /api/v1/loans/{loanNumber}`, `@Pattern` validation (alphanumeric 6–20 chars)
- Verify: `./gradlew build` green; commit

## Phase 4 — Relationship-Accounts Endpoint

**Files:** JPA entities (`CustomerEntity`, `AccountEntity`, `CustomerAccountRelationshipEntity`), JPA repositories, `RelationshipAccountService`, `RelationshipAccountController`, `RelationshipAccountMapper`, DTOs, `CustomerNotFoundException`

- JPA: `@Transactional(readOnly = true)` on service reads; entities never cross service boundary
- Pagination via `Pageable`; stable sort by `account_number`
- When `includeLoanDetails=true`: collect LOAN-type account numbers on current page, fetch in parallel via `CompletableFuture` + bounded executor, fail whole request with 503 on any error/timeout
- Verify: `./gradlew build` green; commit

## Phase 5 — Cross-Cutting Concerns

**Files:** `CorrelationIdFilter`, `MdcTaskDecorator`, `GlobalExceptionHandler`, structured logging config, Actuator config in `application.yaml`

- `CorrelationIdFilter`: read `X-Correlation-Id` header (generate UUID if absent), put in MDC, write to response header
- `GlobalExceptionHandler`: RFC 7807 `ProblemDetail` for 400/404/503/500; never leak stack traces
- Bigtable gRPC and DB errors → 503
- Actuator: expose only `health`, `info`; liveness probe does NOT ping Bigtable or DB
- Verify: `./gradlew build` green; commit

## Phase 6 — OpenAPI / Swagger + API Gateway

**Files:** `OpenApiConfig.java`, controller annotations, `gateway/openapi-gateway.yaml`

- springdoc: force OpenAPI 3.0 output (not 3.1); Swagger UI on in local, off in prod
- Annotate all operations, parameters, response schemas, error responses
- `gateway/openapi-gateway.yaml`: OpenAPI 3.0 with `x-google-backend` (Cloud Run URL placeholder), `x-google-api-management`, API key security scheme
- Verify: `./gradlew build` green; commit

## Phase 7 — Tests

**Files:** Service unit tests, mapper unit tests, `@WebMvcTest` controller slice tests, integration test stubs

- Unit: `LoanServiceTest`, `RelationshipAccountServiceTest` (parallel success/failure/timeout paths), `LoanMapperTest`, `RelationshipAccountMapperTest`
- Slice: `LoanControllerTest`, `RelationshipAccountControllerTest` (success, validation errors, 404, 503, ProblemDetail shape)
- Integration stubs: `@Disabled("REQUIRES_DOCKER: ...")` with `fail("Start Docker Desktop and re-enable")`
- Verify: `./gradlew check` green at ≥80% line coverage; commit

## Phase 8 — Local Dev, Jib, Docs

**Files:** `docker-compose.yml`, `scripts/seed-local.sh`, `db/local/01_schema.sql`, `db/local/02_seed.sql`, `README.md`, `docs/DECISIONS.md`

- `docker-compose.yml`: PostgreSQL 16 + Bigtable emulator
- Seed script: creates table/family in emulator, inserts sample loan rows
- `README.md`: purpose, mermaid diagram, data-source mapping, local run steps, test steps, env var reference, Cloud Run + API Gateway deployment commands
- `docs/DECISIONS.md`: each design decision + rationale + "Assumptions to confirm with client"
- Final: `./gradlew check` green; commit
