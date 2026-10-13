# Loan Data API

Read-only REST API POC exposing loan and customer relationship-account data.
Built on Spring Boot 4.1.1 / Java 17, deployed as a container on GCP Cloud Run
behind GCP API Gateway.

---

## Architecture

```
Client
  │  x-api-key header
  ▼
GCP API Gateway  ◄── openapi-gateway.yaml (gateway/)
  │
  ▼  HTTP (internal VPC)
Cloud Run  (loan-data-api container)
  │
  ├─── GET /api/v1/loans/{loanNumber} ──────────────► Cloud Bigtable
  │                                                    table: loans
  │                                                    CF: details
  │
  └─── GET /api/v1/customers/{id}/relationship-accounts
            │
            ├──────────────────────────────────────► AlloyDB (PostgreSQL)
            │                                         tables: customer,
            │                                                 account,
            │                                                 customer_account_relationship
            │
            └── (includeLoanDetails=true) ──parallel── Cloud Bigtable
                                                        (one fetch per LOAN account
                                                         on the current page)
```

**Data source mapping:**

| API endpoint | Primary store | Optional enrichment |
|---|---|---|
| `GET /loans/{loanNumber}` | Cloud Bigtable `loans` table | — |
| `GET /customers/{id}/relationship-accounts` | AlloyDB `customer_account_relationship` | Cloud Bigtable (when `includeLoanDetails=true`) |

**Parallel-fetch failure policy:** if any Bigtable lookup fails or exceeds the
configured timeout, the entire relationship-accounts request returns 503. Partial
data is never returned. See `docs/DECISIONS.md`.

---

## Prerequisites

- Java 17 JDK
- Docker Desktop (for local PostgreSQL and Bigtable emulator)
- [cbt CLI](https://cloud.google.com/bigtable/docs/cbt-reference) for seeding
  Bigtable (`gcloud components install cbt`)
- Gradle wrapper (committed — no separate Gradle installation needed)

---

## Running locally

### 1. Start the local stack

```bash
docker compose up -d
```

Wait for both containers to pass their health checks:

```bash
docker compose ps   # postgres and bigtable-emulator should show "healthy"
```

### 2. Seed Bigtable

```bash
BIGTABLE_EMULATOR_HOST=localhost:9035 bash scripts/seed-local.sh
```

PostgreSQL is seeded automatically by Spring Boot on first startup from
`src/main/resources/db/local/`.

### 3. Start the API

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

The app starts on port 8080.

### 4. Try it in Swagger UI

Open [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)

**Sample requests:**

```bash
# Loan detail (Bigtable)
curl http://localhost:8080/api/v1/loans/LN100001

# Relationship accounts (AlloyDB only)
curl "http://localhost:8080/api/v1/customers/CUST-0001/relationship-accounts"

# Relationship accounts with Bigtable enrichment
curl "http://localhost:8080/api/v1/customers/CUST-0001/relationship-accounts?includeLoanDetails=true"

# Health probes
curl http://localhost:8080/actuator/health/liveness
curl http://localhost:8080/actuator/health/readiness
```

---

## Running tests

```bash
# All checks (Spotless format, unit tests, JaCoCo coverage gate)
./gradlew check

# Tests only (without coverage gate)
./gradlew test

# Coverage report (HTML)
open build/reports/jacoco/test/html/index.html
```

Integration tests require Docker and are currently `@Disabled("REQUIRES_DOCKER")`.
To enable them, start Docker Desktop, then remove the `@Disabled` annotations in:

- `src/test/java/.../integration/LoanIntegrationTest.java`
- `src/test/java/.../integration/RelationshipAccountIntegrationTest.java`

---

## Configuration reference

All values are read from environment variables. Non-starred items have defaults.

| Environment variable | Description | Default |
|---|---|---|
| `PORT` | HTTP listen port | `8080` |
| `BIGTABLE_PROJECT_ID` | GCP project ID | *(required)* |
| `BIGTABLE_INSTANCE_ID` | Bigtable instance ID | *(required)* |
| `BIGTABLE_APP_PROFILE_ID` | Bigtable app profile | `default` |
| `BIGTABLE_READ_TIMEOUT_MS` | Bigtable read timeout (ms) | `10000` |
| `BIGTABLE_EMULATOR_HOST` | Emulator address (local only) | *(empty = prod)* |
| `ALLOYDB_INSTANCE_URI` | AlloyDB instance URI (prod) | *(required in prod)* |
| `ALLOYDB_DB_NAME` | Database name | *(required in prod)* |
| `ALLOYDB_DB_USER` | IAM database user | *(required in prod)* |
| `HIKARI_MAX_POOL_SIZE` | HikariCP max connections | `10` |
| `HIKARI_MIN_IDLE` | HikariCP min idle | `2` |
| `HIKARI_CONNECTION_TIMEOUT_MS` | Connection timeout (ms) | `5000` |
| `HIKARI_MAX_LIFETIME_MS` | Max connection lifetime (ms) | `1800000` |
| `EXECUTOR_CORE_POOL_SIZE` | Bigtable fetch thread pool core size | `4` |
| `EXECUTOR_MAX_POOL_SIZE` | Bigtable fetch thread pool max size | `10` |
| `EXECUTOR_QUEUE_CAPACITY` | Bigtable fetch thread pool queue depth | `50` |
| `LOCAL_DB_HOST` | Local PostgreSQL host (local profile) | `localhost` |
| `LOCAL_DB_PORT` | Local PostgreSQL port | `5432` |
| `LOCAL_DB_NAME` | Local database name | `loandb` |
| `LOCAL_DB_USER` | Local database user | `loanuser` |
| `LOCAL_DB_PASSWORD` | Local database password | `loanpass` |

---

## Cloud Run + API Gateway deployment

> **Documentation only — do not run against a real GCP project during evaluation.**

### 1. Build and push the container image

```bash
IMAGE_NAME=gcr.io/YOUR_PROJECT_ID/loan-data-api:latest \
  ./gradlew jib
```

Jib builds and pushes directly without a local Docker daemon.

### 2. Deploy to Cloud Run

```bash
gcloud run deploy loan-data-api \
  --image gcr.io/YOUR_PROJECT_ID/loan-data-api:latest \
  --region us-central1 \
  --no-allow-unauthenticated \
  --set-env-vars "BIGTABLE_PROJECT_ID=YOUR_PROJECT_ID" \
  --set-env-vars "BIGTABLE_INSTANCE_ID=YOUR_INSTANCE" \
  --set-env-vars "ALLOYDB_INSTANCE_URI=projects/YOUR_PROJECT/locations/us-central1/clusters/YOUR_CLUSTER/instances/YOUR_INSTANCE" \
  --set-env-vars "ALLOYDB_DB_NAME=loandb" \
  --set-env-vars "ALLOYDB_DB_USER=loan-api-sa@YOUR_PROJECT.iam" \
  --service-account loan-api-sa@YOUR_PROJECT.iam.gserviceaccount.com \
  --spring-profiles-active prod
```

The service account must have:
- `roles/bigtable.reader` on the Bigtable instance
- `roles/alloydb.databaseUser` and Cloud SQL/AlloyDB IAM login

### 3. Create the API Gateway

```bash
# Replace <CLOUD_RUN_URL> in gateway/openapi-gateway.yaml with the deployed URL, then:

gcloud api-gateway apis create loan-data-api \
  --project YOUR_PROJECT_ID

gcloud api-gateway api-configs create v1 \
  --api loan-data-api \
  --openapi-spec gateway/openapi-gateway.yaml \
  --project YOUR_PROJECT_ID \
  --backend-auth-service-account gateway-sa@YOUR_PROJECT.iam.gserviceaccount.com

gcloud api-gateway gateways create loan-gateway \
  --api loan-data-api \
  --api-config v1 \
  --location us-central1 \
  --project YOUR_PROJECT_ID
```

### 4. Create an API key and test

```bash
gcloud services enable apigateway.googleapis.com --project YOUR_PROJECT_ID

# Create an API key restricted to the loan-data-api service
GATEWAY_URL=$(gcloud api-gateway gateways describe loan-gateway \
  --location us-central1 --project YOUR_PROJECT_ID --format 'value(defaultHostname)')

curl -H "x-api-key: YOUR_API_KEY" \
  "https://${GATEWAY_URL}/api/v1/loans/LN100001"
```

---

## Project structure

```
src/main/java/com/example/lending/loandata/
├── config/            Spring @Configuration beans (Bigtable, AlloyDB, thread pool, OpenAPI)
├── controller/        HTTP layer — annotations only, no business logic
├── service/           Business logic, coordinates both data stores
├── repository/
│   ├── alloydb/       Spring Data JPA repositories
│   └── bigtable/      Bigtable read operations
├── mapper/            Maps raw data (Maps / JPA entities) to DTO records
├── model/
│   ├── dto/           Java record DTOs
│   └── entity/        JPA entity classes
├── exception/         Domain exceptions + GlobalExceptionHandler (RFC 7807)
└── observability/     CorrelationIdFilter, MdcTaskDecorator

gateway/               openapi-gateway.yaml — GCP API Gateway config
scripts/               seed-local.sh — seeds Bigtable emulator
docs/                  DECISIONS.md — design decisions and assumptions
```
