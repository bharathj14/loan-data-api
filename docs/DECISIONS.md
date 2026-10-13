# Design Decisions

## Data layer

| Decision | Rationale |
|---|---|
| Bigtable row key = loan number | Natural, high-cardinality key; avoids hot-spotting; matches the only read pattern (point lookup by loan number). |
| Single column family `details` for all loan columns | Simple read model; all columns are read together on every request; no benefit from splitting families. |
| `findLoanRow` reads all columns; `findLoanSummaryRow` reads 5 via regex filter | Enrichment path only needs a summary; sending fewer bytes over gRPC reduces latency for parallel fan-out requests. |
| `@Transactional(readOnly = true)` on all AlloyDB reads | Tells Hibernate to skip dirty-checking; signals to the JDBC driver that the connection can be routed to a read replica. |
| `JOIN FETCH` in the relationship-accounts query | Avoids N+1 selects; account data is always needed alongside relationship rows; single round-trip to the database. |
| `ddl-auto: validate` in all profiles (including local) | Schema divergence should fail loudly at startup, not silently at runtime; migrations must be applied deliberately. |
| Spring Data JPA for AlloyDB, not plain JDBC | Reduces boilerplate for simple queries; `Page<T>` pagination support built-in; the query complexity does not warrant raw SQL. |

## API design

| Decision | Rationale |
|---|---|
| Pagination default 20, max 100 | Prevents unbounded result sets; max 100 bounds the number of parallel Bigtable lookups to a predictable ceiling. |
| Stable sort by `account_number` on relationship-accounts | Reproducible pagination; clients can reliably resume a page walk. |
| RFC 7807 ProblemDetail for all errors | Standard error format expected by API consumers; avoids leaking internal details (stack traces, SQL, row keys). |
| `LoanNotFoundException` / `CustomerNotFoundException` are unchecked | Domain exceptions that propagate through the service layer; catching at the `@RestControllerAdvice` boundary keeps service code clean. |
| No write endpoints | POC scope; read-only reduces attack surface and eliminates mutation-related concerns. |

## Parallel Bigtable enrichment (relationship-accounts)

| Decision | Rationale |
|---|---|
| Parallel fan-out, not sequential | Sequential lookups for a page of 20 LOAN accounts would add up to 20 × Bigtable latency; parallel brings it down to ~1× worst-case. |
| Fail-whole-request on any Bigtable error (503, no partial data) | Partial enrichment would silently mislead the consumer — some accounts have loan details, others silently do not. The client cannot distinguish "no data" from "fetch failed". Explicit 503 allows the caller to retry or degrade gracefully. |
| `CompletableFuture.allOf(...).get(timeout)` with configurable timeout | Hard ceiling below the API Gateway 15 s deadline; avoids holding threads indefinitely on slow Bigtable responses. |
| Bounded `ThreadPoolTaskExecutor` with MDC decorator | Prevents unbounded thread creation; `MdcTaskDecorator` propagates `correlationId` from the request thread so Bigtable fetch logs carry the same ID. |

## Security and observability

| Decision | Rationale |
|---|---|
| Application Default Credentials (no hardcoded keys) | Credentials are injected by the Cloud Run execution environment; nothing to rotate or accidentally commit. |
| AlloyDB IAM authentication (`enableIamAuth=true`, no passwords) | Password-free auth; access controlled via IAM policy; no credential rotation needed. |
| Correlation ID filter at `HIGHEST_PRECEDENCE` | Every log line emitted during the request carries the same ID; simplifies distributed tracing without a full tracing SDK. |
| MDC cleared in `finally` block (filter and decorator) | Prevents correlation ID from leaking into subsequent requests that reuse the same thread from the pool. |
| Liveness probe excludes Bigtable and AlloyDB | A slow external store must not restart a healthy pod; liveness covers only JVM health (`livenessState`). Readiness includes `db` (AlloyDB) so the pod is taken out of rotation if the DB is unreachable. |
| Mask loan numbers to last 4 digits in logs | PII / sensitive data policy; full account numbers must not appear in logs. |

## Build and infrastructure

| Decision | Rationale |
|---|---|
| Jib for container builds | No Docker daemon needed in CI; reproducible layered image; non-root user and `MaxRAMPercentage` set in the plugin config rather than a Dockerfile. |
| `spring-boot-starter-test` replaces four fake `spring-boot-starter-*-test` artifacts | Spring Boot 4 removed `@WebMvcTest` from `spring-boot-test-autoconfigure`; controller tests now use `MockMvcBuilders.standaloneSetup`. |
| JaCoCo `classDirectories.setFrom` for exclusions | More reliable than `violationRules.rule.excludes` (which does not reduce the bundle ratio in practice); excludes config beans and Bigtable repository that cannot be exercised without live infrastructure. |
| OpenAPI 3.0 output via `springdoc.api-docs.openapi-version: 3.0.3` | GCP API Gateway does not accept OpenAPI 3.1 documents. |

---

## Assumptions to confirm with the client

| # | Assumption | Impact if wrong |
|---|---|---|
| 1 | Loan number is the Bigtable row key (not a composite key or hash). | Row read pattern and validation regex must change. |
| 2 | The `details` column family holds all loan columns listed in the spec. | Column names in `BigtableRepository` must be adjusted. |
| 3 | The three AlloyDB tables (`customer`, `account`, `customer_account_relationship`) exist with the schema defined in `db/local/01_schema.sql`. | Entity mappings and JPA validation will fail at startup. |
| 4 | IAM authentication is used for both Bigtable (ADC) and AlloyDB (`enableIamAuth=true`). No passwords needed. | `AlloyDbDataSourceConfig$ProdDataSource` must add password/SSL config. |
| 5 | Partial Bigtable enrichment is unacceptable — caller must receive a 503 if any lookup fails. | Failure policy must change to return partial data and log warnings. |
| 6 | The API is stateless; no session or token beyond the API key at the gateway layer. | Auth middleware needed. |
| 7 | Pagination of 100 rows per page and 10 parallel Bigtable lookups per page are within the Bigtable read quota and cost envelope. | Executor pool size and page maximum must be renegotiated. |
| 8 | The Bigtable `app-profile-id` used in production is a single-cluster profile (not multi-cluster) — consistency requirements should be confirmed. | App profile and read semantics in `BigtableConfig` may need adjustment. |
| 9 | Cloud Run is the correct runtime (vs GKE) — horizontal scaling, no long-lived connections to AlloyDB via pgBouncer, no VPC-native traffic requirements. | Deployment and HikariCP settings must change. |
