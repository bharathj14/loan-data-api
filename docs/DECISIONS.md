# Design Decisions

| Decision | Rationale |
|---|---|
| AlloyDB via AlloyDB Java Connector (IAM auth) | Zero password rotation; connector handles mTLS automatically |
| Bigtable row key = loan number | Natural access pattern; single-row reads per loan |
| Three data sources combined in service layer only | Keeps repositories/clients as pure I/O; no cross-source logic leaks |
| Spring RestClient + HTTP interface | Boot 4 / Spring Framework 7 standard; no extra dependencies |
| GET-only retry (5xx/429), POST never retried | POST mutations are not idempotent by default; POST sends Idempotency-Key instead |
| Idempotency-Key propagated from caller or generated | Caller can retry at their layer without duplicate mutations |
| RFC 7807 ProblemDetail from single @RestControllerAdvice | One place for all error shaping; no stack traces leak to clients |
| Stateless Spring Security (no session) | Cloud Run is horizontally scaled; sessions would need shared store |
| JaCoCo 80% gate with exclusions for config/entities/bigtable | Config beans and entities are framework-driven; coverage gate applies to business logic |
| downstream-stub in same repo | POC must run standalone on a laptop; stub is clearly marked TEMPORARY |
| gateway/ and deploy/ in same repo | POC convenience; these belong in the infrastructure repo in production |

## Assumptions to confirm with the client

- **Data ownership per store**: Is AlloyDB the system of record for all account data, or does Bigtable hold the authoritative loan state?
- **Partial-failure policy**: If Bigtable is unreachable during a relationship-accounts call that requests loan enrichment, should the API return accounts without enrichment (degraded) or fail the entire request (503)? Currently: fails the request.
- **Downstream auth**: What authentication does the downstream service require? (OAuth2 client credentials? GCP service-to-service ID token? None for POC.)
- **Idempotency semantics**: Is the downstream service truly idempotent on `Idempotency-Key`, or does it acknowledge but not deduplicate?
- **Ended relationships**: Should ended relationships (where `relationship_end_date IS NOT NULL`) be returned by default, or filtered out? Currently: filtered out.
- **Row key design**: Is loan number always the Bigtable row key, or does the key include a prefix/hash for tablet distribution at scale?
- **Pagination defaults**: Are the default page size (20) and max size (100) acceptable for production load?
