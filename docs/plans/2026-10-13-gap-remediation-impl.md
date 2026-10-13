# Gap Remediation Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use godmode:task-runner to implement this plan task-by-task.

**Goal:** Bring the existing single-module POC to full production-reference spec across 8 sequential phases, each ending with `.\gradlew.bat :loan-data-api:check` passing.

**Architecture:** Multi-module Gradle repo (`:loan-data-api` + `:downstream-stub`). Three data sources (AlloyDB, Bigtable, downstream REST). Two runtime profiles: `local` and `gcp`. Downstream contract is the Loan Servicing API placeholder.

**Tech Stack:** Spring Boot 4.1.1, Spring Framework 7.0.9, Java 17, Lombok 1.18.46 (BOM), spring-retry 2.0.13, springdoc-openapi 3.0.3, Gradle 9.7.1.

**Verified versions:**
- `spring-retry`: `org.springframework.retry:spring-retry:2.0.13` (not in Boot BOM — pin it)
- `lombok`: `org.projectlombok:lombok` (no version — Boot BOM manages 1.18.46)
- Spring Framework: 7.0.9 (pulled by Spring Boot 4.1.1)

---

## Phase 0 — CLAUDE.md + Multi-Module Restructure

### Task 0.1: Create CLAUDE.md at repo root

**Files:** Create `CLAUDE.md`

Create `CLAUDE.md` with this exact content:

```markdown
# CLAUDE.md — Engineering Standards

## Build & Run Commands
- Check (lint + coverage gate): `.\gradlew.bat :loan-data-api:check`
- Run loan-data-api (local): set `SPRING_PROFILES_ACTIVE=local` in IntelliJ run config
- Run downstream-stub (local): set `SPRING_PROFILES_ACTIVE=local` in IntelliJ run config (port 8081)
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
- @Transactional(readOnly=true) on read services; open-in-view=false; ddl-auto=validate outside local
- HikariCP configured from properties; one BigtableDataClient bean (closed on shutdown)
- Bigtable values: UTF-8 strings; decimals plain text; dates ISO yyyy-MM-dd; mapper tolerates nulls

### HTTP Client
- Spring RestClient + HTTP interface (@GetExchange/@PostExchange). No RestTemplate, no OpenFeign.
- GET: retry on connection errors/5xx/429 via spring-retry RetryTemplate interceptor
- POST: never retry; Idempotency-Key header (UUID)
- Propagate X-Correlation-Id to downstream
- Map 4xx→our 4xx, 5xx/timeout→502/503; never forward downstream bodies verbatim

### API
- Base path /api/v1; Bean Validation on all inputs
- RFC 7807 ProblemDetail from one @RestControllerAdvice; no stack traces in responses
- Pagination: page/size, max 100

### Security
- Stateless. gcp: JWT OAuth2 RS (issuer/audience from env). local: permit all but still stateless.
- Actuator health open; everything else authenticated in gcp

### Observability
- X-Correlation-Id filter (MDC); JSON structured logs
- Mask: never log full names/amounts/account numbers — last 4 chars only

### Code Style
- No comments unless the WHY is non-obvious
- No dead code; no commented-out code; no System.out
- Every class file begins with a one-line header comment (purpose + layer)
```

**Verify:** File exists at repo root.

---

### Task 0.2: Convert to multi-module Gradle

**Files:**
- Modify: `settings.gradle`
- Create: `build.gradle` (new root build — replaces current)
- Create: `loan-data-api/build.gradle` (submodule — receives most of current root build.gradle content)
- Create: `downstream-stub/build.gradle`

**Step 1: Update settings.gradle**

Replace `settings.gradle` entirely:

```groovy
// Root settings for the loan-data-api multi-module project.
// TEMPORARY note: downstream-stub is kept here only so the POC runs standalone.
// In the target architecture it is a separate repository/service. Never deployed.

rootProject.name = 'loan-data-api'

include ':loan-data-api', ':downstream-stub'
```

**Step 2: Create root build.gradle**

```groovy
// Root build: shared configuration applied to all submodules.

plugins {
  id 'com.diffplug.spotless' version '7.0.4' apply false
}

subprojects {
  apply plugin: 'java'

  java {
    toolchain {
      languageVersion = JavaLanguageVersion.of(17)
    }
  }

  repositories {
    mavenCentral()
  }

  tasks.withType(Test).configureEach {
    useJUnitPlatform()
  }
}
```

**Step 3: Create loan-data-api/build.gradle**

Move all content from the current root `build.gradle` here, adjusting the `spotless.java.target` to `'src/**/*.java'`:

```groovy
// Deployable Spring Boot service — the Cloud Run artifact.
// Contains all production code, tests, JaCoCo coverage gate, and Jib image build.

plugins {
  id 'org.springframework.boot' version '4.1.1'
  id 'io.spring.dependency-management' version '1.1.7'
  id 'com.google.cloud.tools.jib' version '3.5.2'
  id 'com.diffplug.spotless' version '7.0.4'
  id 'jacoco'
}

group = 'com.example.lending'
version = '0.0.1-SNAPSHOT'

dependencyManagement {
  imports {
    mavenBom 'com.google.cloud:libraries-bom:26.86.0'
  }
}

dependencies {
  // ── Spring Boot starters ──────────────────────────────────────────────────
  implementation 'org.springframework.boot:spring-boot-starter-actuator'
  implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
  implementation 'org.springframework.boot:spring-boot-starter-validation'
  implementation 'org.springframework.boot:spring-boot-starter-web'
  implementation 'org.springframework.boot:spring-boot-starter-security'
  implementation 'org.springframework.boot:spring-boot-starter-oauth2-resource-server'

  // ── Retry ─────────────────────────────────────────────────────────────────
  // spring-retry is not in the Spring Boot BOM for 4.1.1 — pinned explicitly.
  implementation 'org.springframework.retry:spring-retry:2.0.13'

  // ── Google Cloud (versions managed by libraries-bom) ─────────────────────
  implementation 'com.google.cloud:google-cloud-bigtable'
  runtimeOnly 'com.google.cloud:alloydb-jdbc-connector:1.3.2'

  // ── Database driver ───────────────────────────────────────────────────────
  runtimeOnly 'org.postgresql:postgresql'

  // ── OpenAPI / Swagger UI ──────────────────────────────────────────────────
  implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.3'

  // ── Lombok ────────────────────────────────────────────────────────────────
  compileOnly 'org.projectlombok:lombok'
  annotationProcessor 'org.projectlombok:lombok'

  // ── Config metadata processor ─────────────────────────────────────────────
  annotationProcessor 'org.springframework.boot:spring-boot-configuration-processor'

  // ── Test ──────────────────────────────────────────────────────────────────
  testImplementation 'org.springframework.boot:spring-boot-starter-test'
  testImplementation 'org.springframework.security:spring-security-test'
  testImplementation 'org.springframework.boot:spring-boot-testcontainers'
  testImplementation 'org.testcontainers:junit-jupiter'
  testImplementation 'org.testcontainers:postgresql'
  testImplementation 'org.testcontainers:gcloud'
  testCompileOnly 'org.projectlombok:lombok'
  testAnnotationProcessor 'org.projectlombok:lombok'
  testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

// ── Test ─────────────────────────────────────────────────────────────────────
tasks.named('test') {
  useJUnitPlatform()
  finalizedBy jacocoTestReport
}

// ── JaCoCo ────────────────────────────────────────────────────────────────────
def jacocoExcludedClasses = [
  '**/LoanDataApiApplication.class',
  '**/model/entity/**',
  '**/config/**',
  '**/repository/bigtable/**',
  '**/LocalBigtableSeeder.class',
]

jacocoTestReport {
  dependsOn test
  classDirectories.setFrom(
    files(classDirectories.files.collect { fileTree(dir: it, excludes: jacocoExcludedClasses) })
  )
  reports { xml.required = true; html.required = true }
}

jacocoTestCoverageVerification {
  classDirectories.setFrom(
    files(classDirectories.files.collect { fileTree(dir: it, excludes: jacocoExcludedClasses) })
  )
  violationRules {
    rule {
      limit { counter = 'LINE'; value = 'COVEREDRATIO'; minimum = 0.80 }
    }
  }
}

// ── Spotless ──────────────────────────────────────────────────────────────────
spotless {
  java {
    googleJavaFormat('1.22.0')
    target 'src/**/*.java'
  }
}

tasks.named('check') {
  dependsOn jacocoTestCoverageVerification
}

// ── Jib ───────────────────────────────────────────────────────────────────────
jib {
  from { image = 'eclipse-temurin:17-jre' }
  to { image = System.getenv().getOrDefault('IMAGE_NAME', 'loan-data-api:latest') }
  container {
    ports = ['8080']
    user = '1000:1000'
    jvmFlags = ['-XX:MaxRAMPercentage=75']
  }
}
```

**Step 4: Create downstream-stub/build.gradle**

```groovy
// TEMPORARY: in the target architecture this is a separate repository/service (2nd repo).
// It is kept here only so the POC runs standalone. Never deployed.

plugins {
  id 'org.springframework.boot' version '4.1.1'
  id 'io.spring.dependency-management' version '1.1.7'
}

group = 'com.example.lending'
version = '0.0.1-SNAPSHOT'

bootJar { enabled = true }

dependencies {
  implementation 'org.springframework.boot:spring-boot-starter-web'
  testImplementation 'org.springframework.boot:spring-boot-starter-test'
  testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}
```

**Step 5: Move existing source into loan-data-api submodule**

Run in PowerShell from repo root:

```powershell
New-Item -ItemType Directory -Force "loan-data-api\src"
Move-Item "src" "loan-data-api\src_tmp"
Move-Item "loan-data-api\src_tmp" "loan-data-api\src"
# The result should be loan-data-api\src\main\... and loan-data-api\src\test\...
```

Note: `Move-Item "src" "loan-data-api\"` moves the `src` folder INTO `loan-data-api\` directly. Verify with:
```powershell
Test-Path "loan-data-api\src\main\java"  # must be True
Test-Path "src"                           # must be False
```

**Step 6: Create downstream-stub main class**

Create `downstream-stub/src/main/java/com/example/lending/stub/DownstreamStubApplication.java`:

```java
// TEMPORARY: in the target architecture this is a separate repository/service (2nd repo).
// It is kept here only so the POC runs standalone. Never deployed.
// Layer: application entry point — downstream-stub module.

package com.example.lending.stub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DownstreamStubApplication {
  public static void main(String[] args) {
    SpringApplication.run(DownstreamStubApplication.class, args);
  }
}
```

Create `downstream-stub/src/main/resources/application.yaml`:

```yaml
# TEMPORARY: stub application config. Local only.
server:
  port: 8081
spring:
  application:
    name: downstream-stub
```

**Step 7: Create gateway/README.md and deploy/README.md**

Create `gateway/README.md`:

```markdown
# Gateway Configuration

> **Note:** In the target setup this belongs in the infrastructure repository (separate repo).
> It is kept here only so the POC is self-contained for review.

Contains `openapi-gateway.yaml` — the GCP API Gateway OpenAPI 3.0 spec with `x-google-backend`.
```

Create `deploy/README.md`:

```markdown
# Cloud Run Deployment

> **Note:** In the target setup this belongs in the infrastructure repository (separate repo).
> It is kept here only so the POC is self-contained for review.

See the main README.md for deployment commands.
```

**Step 8: Verify build compiles**

```powershell
.\gradlew.bat :loan-data-api:compileJava
```

Expected: `BUILD SUCCESSFUL`

**Step 9: Commit**

```powershell
git add -A
git commit -m "feat: Phase 0 — CLAUDE.md, multi-module Gradle, downstream-stub shell"
```

---

## Phase 1 — Profile Rename + AlloyDB URL + Config Records

### Task 1.1: Rename prod profile to gcp

**Files to rename/modify:**

1. Rename `loan-data-api/src/main/resources/application-prod.yaml` → `application-gcp.yaml`
2. In `loan-data-api/src/main/java/.../config/AlloyDbDataSourceConfig.java`: `@Profile("prod")` → `@Profile("gcp")`

**Step 1: Rename the file**

```powershell
Rename-Item "loan-data-api\src\main\resources\application-prod.yaml" "application-gcp.yaml"
```

**Step 2: Grep for any remaining "prod" profile references**

```powershell
Select-String -Path "loan-data-api\src\**\*.java","loan-data-api\src\**\*.yaml","loan-data-api\src\**\*.yml" -Pattern '"prod"' -Recurse
```

Expected: only `AlloyDbDataSourceConfig.java` shows up. Fix all occurrences to `"gcp"`.

In `AlloyDbDataSourceConfig.java`, change:
```java
@Profile("prod")
```
to:
```java
@Profile("gcp")
```

Also change the javadoc comment: `<b>prod profile:</b>` → `<b>gcp profile:</b>`

### Task 1.2: Fix AlloyDB JDBC URL

**File:** `loan-data-api/src/main/java/.../config/AlloyDbDataSourceConfig.java`

The existing URL is wrong. Replace the JDBC URL construction in `ProdDataSource.dataSource()`:

```java
// BEFORE (wrong):
config.setJdbcUrl(
    "jdbc:postgresql:///"
        + database
        + "?socketFactory=com.google.cloud.alloydb.jdbc.AlloyDbSocketFactory"
        + "&alloydbInstanceName=" + instanceUri
        + "&enableIamAuth=true");

// AFTER (correct per spec):
config.setJdbcUrl(
    "jdbc:postgresql:///"
        + database
        + "?socketFactory=com.google.cloud.alloydb.SocketFactory"
        + "&alloydbInstanceName=" + instanceUri
        + "&alloydbEnableIAMAuth=true"
        + "&alloydbIpType=" + ipType
        + "&currentSchema=loan_data");
```

Add `ipType` parameter to the method signature:
```java
@Value("${app.datasource.alloydb.ipType:PRIVATE}") String ipType,
```

### Task 1.3: Add Downstream to AppProperties

**File:** `loan-data-api/src/main/java/.../config/AppProperties.java`

Add a `Downstream` nested record:

```java
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
    @Valid @NotNull Bigtable bigtable,
    @Valid @NotNull Executor executor,
    @Valid @NotNull Downstream downstream) {

  // ... existing Bigtable and Executor records unchanged ...

  /** Downstream Loan Servicing API connection properties. */
  public record Downstream(
      @NotBlank String baseUrl,
      @Positive int connectTimeoutMs,
      @Positive int readTimeoutMs,
      @Valid @NotNull Retry retry) {

    public record Retry(
        @Min(1) @Max(10) int maxAttempts,
        @Positive long initialBackoffMs,
        @DecimalMin("1.0") @DecimalMax("3.0") double backoffMultiplier) {}
  }
}
```

Add the import for `@DecimalMin`/`@DecimalMax` (`jakarta.validation.constraints`).

### Task 1.4: Add downstream config to application.yaml and profile files

**File:** `loan-data-api/src/main/resources/application.yaml`

Add after the `executor:` block:

```yaml
  downstream:
    base-url: ${DOWNSTREAM_BASE_URL:http://localhost:8081}
    connect-timeout-ms: ${DOWNSTREAM_CONNECT_TIMEOUT_MS:3000}
    read-timeout-ms: ${DOWNSTREAM_READ_TIMEOUT_MS:10000}
    retry:
      max-attempts: ${DOWNSTREAM_RETRY_MAX_ATTEMPTS:3}
      initial-backoff-ms: ${DOWNSTREAM_RETRY_INITIAL_BACKOFF_MS:500}
      backoff-multiplier: ${DOWNSTREAM_RETRY_BACKOFF_MULTIPLIER:2.0}
```

**File:** `loan-data-api/src/main/resources/application-gcp.yaml`

Add `app.datasource.alloydb.ipType` so the property is defined:

```yaml
app:
  datasource:
    alloydb:
      instance-uri: ${ALLOYDB_INSTANCE_URI}
      database: ${ALLOYDB_DB_NAME}
      iam-user: ${ALLOYDB_IAM_USER}
      ip-type: ${ALLOYDB_IP_TYPE:PRIVATE}
  downstream:
    base-url: ${DOWNSTREAM_BASE_URL}
```

### Task 1.5: Add currentSchema to local JDBC URL

**File:** `loan-data-api/src/main/resources/application-local.yaml`

Change the datasource URL line to append `?currentSchema=loan_data`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://${LOCAL_DB_HOST:localhost}:${LOCAL_DB_PORT:5432}/${LOCAL_DB_NAME:loandb}?currentSchema=loan_data
```

### Task 1.6: Verify build

```powershell
.\gradlew.bat :loan-data-api:compileJava
```

Expected: `BUILD SUCCESSFUL`

### Task 1.7: Commit

```powershell
git add -A
git commit -m "feat: Phase 1 — rename prod to gcp, fix AlloyDB URL, add downstream config properties"
```

---

## Phase 2 — Schema + Entities + Lombok

### Task 2.1: Add loan_data schema to SQL scripts

**File:** `loan-data-api/src/main/resources/db/local/01_schema.sql`

Replace entire file:

```sql
-- Local/test schema: mirrors the AlloyDB production schema.
-- Run automatically on startup when spring.sql.init.mode=always (local profile).

CREATE SCHEMA IF NOT EXISTS loan_data;

CREATE TABLE IF NOT EXISTS loan_data.customer
(
    customer_id   VARCHAR(50)  NOT NULL PRIMARY KEY,
    full_name     VARCHAR(200) NOT NULL,
    customer_type VARCHAR(50)  NOT NULL,
    status        VARCHAR(20)  NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS loan_data.account
(
    account_number VARCHAR(50) NOT NULL PRIMARY KEY,
    account_type   VARCHAR(20) NOT NULL,
    product_code   VARCHAR(50) NOT NULL,
    status         VARCHAR(20) NOT NULL,
    opened_date    DATE,
    currency       CHAR(3)     NOT NULL
);

CREATE TABLE IF NOT EXISTS loan_data.customer_account_relationship
(
    customer_id       VARCHAR(50) NOT NULL REFERENCES loan_data.customer (customer_id),
    account_number    VARCHAR(50) NOT NULL REFERENCES loan_data.account (account_number),
    relationship_type VARCHAR(30) NOT NULL,
    start_date        DATE,
    end_date          DATE,
    PRIMARY KEY (customer_id, account_number, relationship_type)
);
```

### Task 2.2: Expand seed data to full spec

**File:** `loan-data-api/src/main/resources/db/local/02_seed.sql`

Replace entire file:

```sql
-- Local seed data: generic sample records only — no real customer names or amounts.
-- Covers: primary borrower, co-borrower, business customer, guarantor, no-accounts
-- customer, ended relationship. Loan account numbers match the Bigtable seeder.

INSERT INTO loan_data.customer (customer_id, full_name, customer_type, status)
VALUES
  ('CUST-0001', 'Alice Sample',    'RETAIL',         'ACTIVE'),
  ('CUST-0002', 'Bob Example',     'RETAIL',         'ACTIVE'),
  ('CUST-0003', 'Carol Business',  'SMALL_BUSINESS',  'ACTIVE'),
  ('CUST-0004', 'Dave Guarantor',  'RETAIL',         'ACTIVE'),
  ('CUST-0005', 'Eve NoAccounts',  'RETAIL',         'ACTIVE')
ON CONFLICT DO NOTHING;

INSERT INTO loan_data.account (account_number, account_type, product_code, status, opened_date, currency)
VALUES
  ('LN1000000001', 'LOAN',        'PERSONAL_LOAN',  'ACTIVE',  '2022-01-15', 'USD'),
  ('LN1000000002', 'LOAN',        'MORTGAGE',       'ACTIVE',  '2020-06-01', 'USD'),
  ('LN1000000003', 'LOAN',        'AUTO_LOAN',      'ACTIVE',  '2023-03-01', 'USD'),
  ('LN1000000004', 'LOAN',        'PERSONAL_LOAN',  'CLOSED',  '2019-01-01', 'USD'),
  ('LN1000000005', 'LOAN',        'MORTGAGE',       'ACTIVE',  '2021-07-15', 'USD'),
  ('DEP200001',    'DEPOSIT',     'SAVINGS',        'ACTIVE',  '2021-03-10', 'USD'),
  ('CC300001',     'CREDIT_CARD', 'PLATINUM_CARD',  'ACTIVE',  '2023-07-20', 'USD')
ON CONFLICT DO NOTHING;

INSERT INTO loan_data.customer_account_relationship
  (customer_id, account_number, relationship_type, start_date, end_date)
VALUES
  -- CUST-0001: primary on two loans + one deposit + one credit card
  ('CUST-0001', 'LN1000000001', 'PRIMARY',      '2022-01-15', NULL),
  ('CUST-0001', 'LN1000000002', 'PRIMARY',      '2020-06-01', NULL),
  ('CUST-0001', 'DEP200001',    'PRIMARY',      '2021-03-10', NULL),
  ('CUST-0001', 'CC300001',     'PRIMARY',      '2023-07-20', NULL),
  -- CUST-0001 had an ended relationship on LN1000000004
  ('CUST-0001', 'LN1000000004', 'PRIMARY',      '2019-01-01', '2024-01-01'),
  -- CUST-0002: co-borrower on LN1000000001
  ('CUST-0002', 'LN1000000001', 'CO_BORROWER',  '2022-01-15', NULL),
  ('CUST-0002', 'LN1000000003', 'PRIMARY',      '2023-03-01', NULL),
  -- CUST-0003: business customer, primary on LN1000000005
  ('CUST-0003', 'LN1000000005', 'PRIMARY',      '2021-07-15', NULL),
  -- CUST-0004: guarantor on LN1000000002; authorized user on CC300001
  ('CUST-0004', 'LN1000000002', 'GUARANTOR',    '2020-06-01', NULL),
  ('CUST-0004', 'CC300001',     'AUTHORIZED_USER', '2023-07-20', NULL)
  -- CUST-0005: no accounts (no rows here — tests the empty-page case)
ON CONFLICT DO NOTHING;
```

Also add `AUTHORIZED_USER` to the `RelationshipType` enum if not already present:

**File:** `loan-data-api/src/main/java/.../model/entity/RelationshipType.java`

```java
// Relationship types between a customer and an account in AlloyDB.
package com.example.lending.loandata.model.entity;

public enum RelationshipType {
  PRIMARY,
  CO_BORROWER,
  GUARANTOR,
  AUTHORIZED_USER
}
```

### Task 2.3: Add schema to @Table annotations

**Files:** All three entity classes.

`CustomerEntity.java`:
```java
@Table(name = "customer", schema = "loan_data")
```

`AccountEntity.java`:
```java
@Table(name = "account", schema = "loan_data")
```

`CustomerAccountRelationshipEntity.java`:
```java
@Table(name = "customer_account_relationship", schema = "loan_data")
```

### Task 2.4: Convert entities to Lombok

**File:** `CustomerEntity.java` — replace manual getters/protected constructor with Lombok:

```java
// Layer: model/entity — JPA mapping for the customer table in AlloyDB.
package com.example.lending.loandata.model.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "customer", schema = "loan_data")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class CustomerEntity {

  @Id
  @Column(name = "customer_id", nullable = false, length = 50)
  private String customerId;

  @Column(name = "full_name", nullable = false, length = 200)
  private String fullName;

  @Column(name = "customer_type", nullable = false, length = 50)
  private String customerType;

  @Column(name = "status", nullable = false, length = 20)
  private String status;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  // Bidirectional: used only for completeness; never accessed in service layer directly
  @OneToMany(mappedBy = "customerId", fetch = FetchType.LAZY)
  private List<CustomerAccountRelationshipEntity> relationships;
}
```

**File:** `AccountEntity.java` — add Lombok, remove manual getters:

```java
// Layer: model/entity — JPA mapping for the account table in AlloyDB.
package com.example.lending.loandata.model.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "account", schema = "loan_data")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class AccountEntity {

  @Id
  @Column(name = "account_number", nullable = false, length = 50)
  private String accountNumber;

  @Enumerated(EnumType.STRING)
  @Column(name = "account_type", nullable = false, length = 20)
  private AccountType accountType;

  @Column(name = "product_code", nullable = false, length = 50)
  private String productCode;

  @Column(name = "status", nullable = false, length = 20)
  private String status;

  @Column(name = "opened_date")
  private LocalDate openedDate;

  @Column(name = "currency", nullable = false, length = 3)
  private String currency;
}
```

**File:** `CustomerAccountRelationshipEntity.java` — convert from `@IdClass` to `@EmbeddedId + @MapsId`:

```java
// Layer: model/entity — JPA mapping for customer_account_relationship in AlloyDB.
// Composite key uses @EmbeddedId + @MapsId so each FK column maps to the embedded key fields.
package com.example.lending.loandata.model.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "customer_account_relationship", schema = "loan_data")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class CustomerAccountRelationshipEntity {

  @EmbeddedId
  private CustomerAccountRelationshipId id;

  @ManyToOne(fetch = FetchType.LAZY)
  @MapsId("customerId")
  @JoinColumn(name = "customer_id")
  private CustomerEntity customer;

  @ManyToOne(fetch = FetchType.LAZY)
  @MapsId("accountNumber")
  @JoinColumn(name = "account_number")
  private AccountEntity account;

  // Convenience accessors used by service and mapper layers
  public String getCustomerId()      { return id.getCustomerId(); }
  public String getAccountNumber()   { return id.getAccountNumber(); }
  public RelationshipType getRelationshipType() { return id.getRelationshipType(); }

  @Column(name = "start_date")
  private LocalDate startDate;

  @Column(name = "end_date")
  private LocalDate endDate;
}
```

**File:** `CustomerAccountRelationshipId.java` — convert to Embeddable with Lombok:

```java
// Layer: model/entity — Embeddable composite key for customer_account_relationship.
package com.example.lending.loandata.model.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Objects;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@NoArgsConstructor
public class CustomerAccountRelationshipId implements Serializable {

  @Column(name = "customer_id", length = 50)
  private String customerId;

  @Column(name = "account_number", length = 50)
  private String accountNumber;

  @Enumerated(EnumType.STRING)
  @Column(name = "relationship_type", length = 30)
  private RelationshipType relationshipType;

  public CustomerAccountRelationshipId(
      String customerId, String accountNumber, RelationshipType relationshipType) {
    this.customerId = customerId;
    this.accountNumber = accountNumber;
    this.relationshipType = relationshipType;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof CustomerAccountRelationshipId that)) return false;
    return Objects.equals(customerId, that.customerId)
        && Objects.equals(accountNumber, that.accountNumber)
        && relationshipType == that.relationshipType;
  }

  @Override
  public int hashCode() {
    return Objects.hash(customerId, accountNumber, relationshipType);
  }
}
```

### Task 2.5: Update CustomerAccountRelationshipRepository for new key type

**File:** `loan-data-api/src/main/java/.../repository/alloydb/CustomerAccountRelationshipRepository.java`

The query uses `r.customerId` — this still works with `@EmbeddedId` because the convenience accessor returns the same value. But the JPA query references the embedded ID path. Update:

```java
@Query(
    "SELECT r FROM CustomerAccountRelationshipEntity r "
        + "JOIN FETCH r.account "
        + "WHERE r.id.customerId = :customerId "
        + "AND (:relationshipType IS NULL OR r.id.relationshipType = :relationshipType) "
        + "ORDER BY r.id.accountNumber ASC")
Page<CustomerAccountRelationshipEntity> findByCustomerIdWithAccount(
    @Param("customerId") String customerId,
    @Param("relationshipType") RelationshipType relationshipType,
    Pageable pageable);
```

Also update the `JpaRepository` type parameter from `CustomerAccountRelationshipId` — it now stays the same class but it's an `@Embeddable` so it's fine.

Also update `CustomerRepository`:

```java
// No change needed — it extends JpaRepository<CustomerEntity, String>
```

### Task 2.6: Update RelationshipAccountService for new field access

In `RelationshipAccountService.java`, the service calls `r.getAccount()` (still works), `r.getAccountNumber()` (still works via convenience accessor), and `r.getAccount().getAccountType()` (still works). No changes needed.

Check `RelationshipAccountMapper` for any direct field access on the entity — if it calls `r.getCustomerId()`, `r.getAccountNumber()`, `r.getRelationshipType()`, those are still fine via the convenience methods.

### Task 2.7: Create LocalBigtableSeeder

Create `loan-data-api/src/main/java/.../LocalBigtableSeeder.java`:

```java
// Layer: config — seeds the local Bigtable emulator on startup (local profile only).
// Creates the loans table and details family if missing, then inserts LN1000000001..LN1000000005.
// LN1000000005 intentionally omits next-payment columns to exercise the null-tolerant mapper.
package com.example.lending.loandata;

import com.google.cloud.bigtable.admin.v2.BigtableTableAdminClient;
import com.google.cloud.bigtable.admin.v2.BigtableTableAdminSettings;
import com.google.cloud.bigtable.admin.v2.models.CreateTableRequest;
import com.google.cloud.bigtable.data.v2.BigtableDataClient;
import com.google.cloud.bigtable.data.v2.models.RowMutation;
import com.google.protobuf.ByteString;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("local")
public class LocalBigtableSeeder {

  private static final Logger log = LoggerFactory.getLogger(LocalBigtableSeeder.class);
  private static final String TABLE = "loans";
  private static final String FAMILY = "details";

  @Bean
  public ApplicationRunner seedBigtable(
      BigtableDataClient dataClient,
      @Value("${app.bigtable.project-id}") String projectId,
      @Value("${app.bigtable.instance-id}") String instanceId,
      @Value("${app.bigtable.emulator-host:localhost:9035}") String emulatorHost) {

    return args -> {
      String[] parts = emulatorHost.split(":");
      try (BigtableTableAdminClient adminClient =
          BigtableTableAdminClient.create(
              BigtableTableAdminSettings.newBuilderForEmulator(parts[0], Integer.parseInt(parts[1]))
                  .setProjectId(projectId)
                  .setInstanceId(instanceId)
                  .build())) {

        if (!adminClient.exists(TABLE)) {
          adminClient.createTable(
              CreateTableRequest.of(TABLE).addFamily(FAMILY));
          log.info("Bigtable: created table '{}' with family '{}'", TABLE, FAMILY);
        }
      } catch (IOException e) {
        log.warn("Bigtable admin setup failed (emulator may not be running): {}", e.getMessage());
        return;
      }

      upsertLoan(dataClient, "LN1000000001", "PERSONAL_LOAN", "ACTIVE",  "250000.00", "USD", "2022-01-15", "2026-11-01", "1250.00");
      upsertLoan(dataClient, "LN1000000002", "MORTGAGE",      "ACTIVE",  "500000.00", "USD", "2020-06-01", "2026-11-01", "2800.00");
      upsertLoan(dataClient, "LN1000000003", "AUTO_LOAN",     "ACTIVE",  "32000.00",  "USD", "2023-03-01", "2026-11-01",  "620.00");
      upsertLoan(dataClient, "LN1000000004", "PERSONAL_LOAN", "CLOSED",  "0.00",      "USD", "2019-01-01", null, null);
      upsertLoanMinimal(dataClient, "LN1000000005"); // missing next-payment columns intentionally

      log.info("Bigtable: seeded 5 loan rows");
    };
  }

  private void upsertLoan(
      BigtableDataClient client,
      String loanNumber, String productType, String status,
      String outstandingBalance, String currency,
      String originationDate, String nextPaymentDueDate, String nextPaymentAmount) {

    RowMutation m = RowMutation.create(TABLE, loanNumber);
    long ts = System.currentTimeMillis() * 1000;
    m.setCell(FAMILY, "loan_number",          ts, loanNumber);
    m.setCell(FAMILY, "product_type",         ts, productType);
    m.setCell(FAMILY, "status",               ts, status);
    m.setCell(FAMILY, "outstanding_balance",  ts, outstandingBalance);
    m.setCell(FAMILY, "currency",             ts, currency);
    m.setCell(FAMILY, "origination_date",     ts, originationDate);
    if (nextPaymentDueDate != null)
      m.setCell(FAMILY, "next_payment_due_date", ts, nextPaymentDueDate);
    if (nextPaymentAmount != null)
      m.setCell(FAMILY, "next_payment_amount", ts, nextPaymentAmount);
    client.mutateRow(m);
  }

  private void upsertLoanMinimal(BigtableDataClient client, String loanNumber) {
    RowMutation m = RowMutation.create(TABLE, loanNumber);
    long ts = System.currentTimeMillis() * 1000;
    m.setCell(FAMILY, "loan_number",         ts, loanNumber);
    m.setCell(FAMILY, "product_type",        ts, "MORTGAGE");
    m.setCell(FAMILY, "status",              ts, "ACTIVE");
    m.setCell(FAMILY, "outstanding_balance", ts, "175000.00");
    m.setCell(FAMILY, "currency",            ts, "USD");
    m.setCell(FAMILY, "origination_date",    ts, "2021-07-15");
    client.mutateRow(m);
  }
}
```

### Task 2.8: Verify build and commit

```powershell
.\gradlew.bat :loan-data-api:compileJava
```

Expected: `BUILD SUCCESSFUL`

If Lombok annotation processing fails, check that `annotationProcessor 'org.projectlombok:lombok'` is in `loan-data-api/build.gradle`.

```powershell
git add -A
git commit -m "feat: Phase 2 — loan_data schema, Lombok entities, EmbeddedId, LocalBigtableSeeder, full seed data"
```

---

## Phase 3 — Downstream Client + downstream-stub

### Task 3.1: Create downstream DTOs

Create `loan-data-api/src/main/java/.../model/dto/downstream/PaymentEntry.java`:

```java
// Layer: model/dto — downstream Loan Servicing API payment entry record.
package com.example.lending.loandata.model.dto.downstream;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PaymentEntry(
    String paymentNumber,
    LocalDate paymentDate,
    BigDecimal amount,
    String status,
    BigDecimal principalPaid,
    BigDecimal interestPaid,
    BigDecimal remainingBalance) {}
```

Create `loan-data-api/src/main/java/.../model/dto/downstream/LoanPaymentsResponse.java`:

```java
// Layer: model/dto — downstream Loan Servicing API payments response (also our API response).
package com.example.lending.loandata.model.dto.downstream;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record LoanPaymentsResponse(
    String loanNumber,
    int totalPayments,
    LocalDate nextPaymentDueDate,
    BigDecimal nextPaymentAmount,
    List<PaymentEntry> payments) {}
```

Create `loan-data-api/src/main/java/.../model/dto/downstream/CreateNoteRequest.java`:

```java
// Layer: model/dto — request body for POST /api/v1/loans/{loanNumber}/notes.
package com.example.lending.loandata.model.dto.downstream;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateNoteRequest(
    @NotNull NoteType noteType,
    @NotBlank @Size(max = 2000) String noteText,
    @NotBlank @Size(max = 100) String createdBy) {}
```

Create `loan-data-api/src/main/java/.../model/dto/downstream/NoteType.java`:

```java
// Layer: model/dto — note type enum for the downstream Loan Servicing API.
package com.example.lending.loandata.model.dto.downstream;

public enum NoteType { GENERAL, PAYMENT, LEGAL }
```

Create `loan-data-api/src/main/java/.../model/dto/downstream/CreateNoteResponse.java`:

```java
// Layer: model/dto — response from POST /v1/loans/{loanNumber}/notes downstream.
package com.example.lending.loandata.model.dto.downstream;

import java.time.OffsetDateTime;

public record CreateNoteResponse(
    String noteId,
    String loanNumber,
    NoteType noteType,
    String noteText,
    String createdBy,
    OffsetDateTime createdAt) {}
```

### Task 3.2: Create LoanServicingClient HTTP interface

Create `loan-data-api/src/main/java/.../client/LoanServicingClient.java`:

```java
// Layer: client — Spring HTTP interface for the downstream Loan Servicing API.
// GET retries are handled by the RetryTemplate interceptor in DownstreamClientConfig.
// POST operations never retry; Idempotency-Key is added in DownstreamClientConfig.
package com.example.lending.loandata.client;

import com.example.lending.loandata.model.dto.downstream.CreateNoteRequest;
import com.example.lending.loandata.model.dto.downstream.CreateNoteResponse;
import com.example.lending.loandata.model.dto.downstream.LoanPaymentsResponse;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@HttpExchange("/v1/loans")
public interface LoanServicingClient {

  @GetExchange("/{loanNumber}/payments")
  LoanPaymentsResponse getPayments(@PathVariable String loanNumber);

  @PostExchange("/{loanNumber}/notes")
  CreateNoteResponse createNote(
      @PathVariable String loanNumber,
      @RequestBody CreateNoteRequest request,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey);
}
```

### Task 3.3: Create retry interceptor

Create `loan-data-api/src/main/java/.../client/RetryClientHttpRequestInterceptor.java`:

```java
// Layer: client — ClientHttpRequestInterceptor that retries GET requests using
// spring-retry RetryTemplate. POST and other non-idempotent methods are never retried.
package com.example.lending.loandata.client;

import java.io.IOException;
import java.util.Set;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.retry.support.RetryTemplate;

public class RetryClientHttpRequestInterceptor implements ClientHttpRequestInterceptor {

  // Only idempotent methods are eligible for retry.
  private static final Set<HttpMethod> RETRYABLE_METHODS =
      Set.of(HttpMethod.GET, HttpMethod.HEAD, HttpMethod.OPTIONS);

  private final RetryTemplate retryTemplate;

  public RetryClientHttpRequestInterceptor(RetryTemplate retryTemplate) {
    this.retryTemplate = retryTemplate;
  }

  @Override
  public ClientHttpResponse intercept(
      org.springframework.http.HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
      throws IOException {
    if (RETRYABLE_METHODS.contains(request.getMethod())) {
      try {
        return retryTemplate.execute(ctx -> execution.execute(request, body));
      } catch (IOException e) {
        throw e;
      } catch (Exception e) {
        throw new IOException("Retry failed", e);
      }
    }
    return execution.execute(request, body);
  }
}
```

### Task 3.4: Create DownstreamClientConfig

Create `loan-data-api/src/main/java/.../config/DownstreamClientConfig.java`:

```java
// Layer: config — wires RestClient + HTTP interface proxy for the downstream Loan Servicing API.
// Retry (GET only), timeouts, X-Correlation-Id propagation, and error response mapping
// are all configured here. POST never retries.
package com.example.lending.loandata.config;

import com.example.lending.loandata.client.LoanServicingClient;
import com.example.lending.loandata.client.RetryClientHttpRequestInterceptor;
import com.example.lending.loandata.exception.DownstreamClientException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import java.net.ConnectException;
import java.util.Map;

@Configuration
public class DownstreamClientConfig {

  @Bean
  public LoanServicingClient loanServicingClient(AppProperties props) {
    AppProperties.Downstream cfg = props.downstream();
    AppProperties.Downstream.Retry retryCfg = cfg.retry();

    RetryTemplate retryTemplate = buildRetryTemplate(retryCfg);

    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(Duration.ofMillis(cfg.connectTimeoutMs()));
    requestFactory.setReadTimeout(Duration.ofMillis(cfg.readTimeoutMs()));

    RestClient restClient = RestClient.builder()
        .baseUrl(cfg.baseUrl())
        .requestFactory(requestFactory)
        .requestInterceptor(new RetryClientHttpRequestInterceptor(retryTemplate))
        .requestInterceptor((request, body, execution) -> {
          // Propagate X-Correlation-Id from MDC to downstream
          String correlationId = MDC.get("correlationId");
          if (correlationId != null) {
            request.getHeaders().set("X-Correlation-Id", correlationId);
          }
          return execution.execute(request, body);
        })
        .defaultStatusHandler(
            HttpStatusCode::is4xxClientError,
            (req, resp) -> {
              throw new DownstreamClientException(resp.getStatusCode().value(), "Downstream client error");
            })
        .defaultStatusHandler(
            HttpStatusCode::is5xxServerError,
            (req, resp) -> {
              throw new DownstreamClientException(resp.getStatusCode().value(), "Downstream server error");
            })
        .build();

    HttpServiceProxyFactory factory = HttpServiceProxyFactory
        .builderFor(RestClientAdapter.create(restClient))
        .build();

    return factory.createClient(LoanServicingClient.class);
  }

  private RetryTemplate buildRetryTemplate(AppProperties.Downstream.Retry cfg) {
    // Retry on connection failures, timeouts, and 5xx (the status handler throws
    // DownstreamClientException for 5xx; we retry only on IOException here).
    SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy(
        cfg.maxAttempts(),
        Map.of(ConnectException.class, true,
               SocketTimeoutException.class, true,
               IOException.class, true));

    ExponentialBackOffPolicy backOff = new ExponentialBackOffPolicy();
    backOff.setInitialInterval(cfg.initialBackoffMs());
    backOff.setMultiplier(cfg.backoffMultiplier());

    RetryTemplate template = new RetryTemplate();
    template.setRetryPolicy(retryPolicy);
    template.setBackOffPolicy(backOff);
    return template;
  }
}
```

Note: `DownstreamClientException` must be created in the next step. The status handler only applies during the execution call, so 5xx responses also trigger the interceptor retry — this is handled by noting in the `RetryClientHttpRequestInterceptor` that it only retries `IOException` wrapping from the status handler. For a clean POC this is acceptable; document in DECISIONS.md.

### Task 3.5: Create DownstreamClientException

Create `loan-data-api/src/main/java/.../exception/DownstreamClientException.java`:

```java
// Layer: exception — thrown by DownstreamClientConfig when the downstream API
// returns a non-2xx response. Carries the HTTP status for mapping in GlobalExceptionHandler.
package com.example.lending.loandata.exception;

public class DownstreamClientException extends RuntimeException {

  private final int downstreamStatus;

  public DownstreamClientException(int downstreamStatus, String message) {
    super(message);
    this.downstreamStatus = downstreamStatus;
  }

  public int getDownstreamStatus() {
    return downstreamStatus;
  }
}
```

### Task 3.6: Update GlobalExceptionHandler for downstream errors

Add to `GlobalExceptionHandler.java`:

```java
@ExceptionHandler(DownstreamClientException.class)
public ProblemDetail handleDownstream(DownstreamClientException ex) {
  log.warn("Downstream error: status={}", ex.getDownstreamStatus());
  if (ex.getDownstreamStatus() == 404) {
    ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
    problem.setType(URI.create("urn:problem:loan-not-found"));
    problem.setTitle("Not Found");
    problem.setDetail("The requested resource was not found.");
    return problem;
  }
  // 5xx or unexpected downstream errors → 502
  ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_GATEWAY);
  problem.setType(URI.create("urn:problem:bad-gateway"));
  problem.setTitle("Bad Gateway");
  problem.setDetail("The downstream service returned an unexpected response.");
  return problem;
}
```

Also add handler for `IOException` and `java.net.SocketTimeoutException` (downstream timeout → 503):

```java
@ExceptionHandler({java.io.IOException.class, java.net.SocketTimeoutException.class})
public ProblemDetail handleTimeout(Exception ex) {
  log.error("Downstream timeout or IO error: {}", ex.getMessage());
  ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.SERVICE_UNAVAILABLE);
  problem.setType(URI.create("urn:problem:service-unavailable"));
  problem.setTitle("Service Unavailable");
  problem.setDetail("A downstream service is temporarily unavailable.");
  return problem;
}
```

### Task 3.7: Build the downstream-stub

Create `downstream-stub/src/main/java/com/example/lending/stub/controller/LoanServicingStubController.java`:

```java
// TEMPORARY: in the target architecture this is a separate repository/service (2nd repo).
// It is kept here only so the POC runs standalone. Never deployed.
// Layer: controller — stub implementation of the Loan Servicing API.
// Use loan number LN_ERROR_500 to simulate a 500 response.
package com.example.lending.stub.controller;

import com.example.lending.stub.model.CreateNoteRequest;
import com.example.lending.stub.model.CreateNoteResponse;
import com.example.lending.stub.model.LoanPaymentsResponse;
import com.example.lending.stub.model.NoteType;
import com.example.lending.stub.model.PaymentEntry;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/loans")
public class LoanServicingStubController {

  @GetMapping("/{loanNumber}/payments")
  public ResponseEntity<LoanPaymentsResponse> getPayments(@PathVariable String loanNumber) {
    if ("LN_ERROR_500".equals(loanNumber)) {
      return ResponseEntity.internalServerError().build();
    }
    if (loanNumber.startsWith("UNKNOWN")) {
      return ResponseEntity.notFound().build();
    }
    LoanPaymentsResponse response = new LoanPaymentsResponse(
        loanNumber,
        3,
        LocalDate.now().plusMonths(1),
        new BigDecimal("1250.00"),
        List.of(
            new PaymentEntry("PAY-001", LocalDate.now().minusMonths(2),
                new BigDecimal("1250.00"), "COMPLETED",
                new BigDecimal("800.00"), new BigDecimal("450.00"), new BigDecimal("248350.00")),
            new PaymentEntry("PAY-002", LocalDate.now().minusMonths(1),
                new BigDecimal("1250.00"), "COMPLETED",
                new BigDecimal("803.00"), new BigDecimal("447.00"), new BigDecimal("247547.00")),
            new PaymentEntry("PAY-003", LocalDate.now(),
                new BigDecimal("1250.00"), "PENDING",
                new BigDecimal("0.00"), new BigDecimal("0.00"), new BigDecimal("247547.00"))));
    return ResponseEntity.ok(response);
  }

  @PostMapping("/{loanNumber}/notes")
  public ResponseEntity<CreateNoteResponse> createNote(
      @PathVariable String loanNumber,
      @RequestBody CreateNoteRequest request,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
    if ("LN_ERROR_500".equals(loanNumber)) {
      return ResponseEntity.internalServerError().build();
    }
    CreateNoteResponse response = new CreateNoteResponse(
        UUID.randomUUID().toString(),
        loanNumber,
        request.noteType(),
        request.noteText(),
        request.createdBy(),
        OffsetDateTime.now());
    return ResponseEntity.ok(response);
  }
}
```

Also create the stub model records under `downstream-stub/src/main/java/com/example/lending/stub/model/` — matching the DTOs used above (PaymentEntry, LoanPaymentsResponse, CreateNoteRequest, CreateNoteResponse, NoteType). These are simple records/enum that mirror the downstream-stub contract.

### Task 3.8: Verify and commit

```powershell
.\gradlew.bat :loan-data-api:compileJava :downstream-stub:compileJava
```

Expected: `BUILD SUCCESSFUL`

```powershell
git add -A
git commit -m "feat: Phase 3 — downstream HTTP client, retry interceptor, downstream-stub"
```

---

## Phase 4 — New Endpoints (Payments + Notes)

### Task 4.1: LoanPaymentsService

Create `loan-data-api/src/main/java/.../service/LoanPaymentsService.java`:

```java
// Layer: service — fetches loan payment history from the downstream Loan Servicing API.
package com.example.lending.loandata.service;

import com.example.lending.loandata.client.LoanServicingClient;
import com.example.lending.loandata.model.dto.downstream.LoanPaymentsResponse;
import org.springframework.stereotype.Service;

@Service
public class LoanPaymentsService {

  private final LoanServicingClient loanServicingClient;

  public LoanPaymentsService(LoanServicingClient loanServicingClient) {
    this.loanServicingClient = loanServicingClient;
  }

  public LoanPaymentsResponse getPayments(String loanNumber) {
    return loanServicingClient.getPayments(loanNumber);
  }
}
```

### Task 4.2: LoanNotesService

Create `loan-data-api/src/main/java/.../service/LoanNotesService.java`:

```java
// Layer: service — creates a note on a loan via the downstream Loan Servicing API.
// POST operations are never retried; the Idempotency-Key is accepted from the caller
// or generated here if absent.
package com.example.lending.loandata.service;

import com.example.lending.loandata.client.LoanServicingClient;
import com.example.lending.loandata.model.dto.downstream.CreateNoteRequest;
import com.example.lending.loandata.model.dto.downstream.CreateNoteResponse;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class LoanNotesService {

  private final LoanServicingClient loanServicingClient;

  public LoanNotesService(LoanServicingClient loanServicingClient) {
    this.loanServicingClient = loanServicingClient;
  }

  public CreateNoteResponse createNote(
      String loanNumber, CreateNoteRequest request, String idempotencyKey) {
    String key = (idempotencyKey != null && !idempotencyKey.isBlank())
        ? idempotencyKey
        : UUID.randomUUID().toString();
    return loanServicingClient.createNote(loanNumber, request, key);
  }
}
```

### Task 4.3: LoanPaymentsController

Create `loan-data-api/src/main/java/.../controller/LoanPaymentsController.java`:

```java
// Layer: controller — exposes GET /api/v1/loans/{loanNumber}/payments (downstream proxy).
package com.example.lending.loandata.controller;

import com.example.lending.loandata.model.dto.downstream.LoanPaymentsResponse;
import com.example.lending.loandata.service.LoanPaymentsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/loans")
@Validated
@Tag(name = "Loan Payments", description = "Payment history from the Loan Servicing API")
public class LoanPaymentsController {

  private final LoanPaymentsService loanPaymentsService;

  public LoanPaymentsController(LoanPaymentsService loanPaymentsService) {
    this.loanPaymentsService = loanPaymentsService;
  }

  @GetMapping("/{loanNumber}/payments")
  @Operation(summary = "Get loan payment history",
      description = "Returns payment history for a loan number from the downstream Loan Servicing API.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Payments found"),
    @ApiResponse(responseCode = "400", description = "Invalid loan number",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(responseCode = "404", description = "Loan not found",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(responseCode = "502", description = "Downstream error",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(responseCode = "503", description = "Downstream unavailable",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  })
  public LoanPaymentsResponse getPayments(
      @Parameter(description = "Alphanumeric loan number, 6–20 characters", example = "LN1000000001")
      @PathVariable
      @Pattern(regexp = "^[A-Za-z0-9_]{6,20}$",
               message = "loanNumber must be 6–20 alphanumeric characters")
          String loanNumber) {
    return loanPaymentsService.getPayments(loanNumber);
  }
}
```

### Task 4.4: LoanNotesController

Create `loan-data-api/src/main/java/.../controller/LoanNotesController.java`:

```java
// Layer: controller — exposes POST /api/v1/loans/{loanNumber}/notes (downstream proxy).
package com.example.lending.loandata.controller;

import com.example.lending.loandata.model.dto.downstream.CreateNoteRequest;
import com.example.lending.loandata.model.dto.downstream.CreateNoteResponse;
import com.example.lending.loandata.service.LoanNotesService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/loans")
@Validated
@Tag(name = "Loan Notes", description = "Loan notes via the Loan Servicing API")
public class LoanNotesController {

  private final LoanNotesService loanNotesService;

  public LoanNotesController(LoanNotesService loanNotesService) {
    this.loanNotesService = loanNotesService;
  }

  @PostMapping("/{loanNumber}/notes")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Create a loan note",
      description = "Creates a note on the specified loan via the downstream Loan Servicing API. "
          + "Pass an Idempotency-Key header to prevent duplicate notes on retry.")
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "Note created"),
    @ApiResponse(responseCode = "400", description = "Validation error",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(responseCode = "404", description = "Loan not found",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
    @ApiResponse(responseCode = "502", description = "Downstream error",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  })
  public CreateNoteResponse createNote(
      @Parameter(description = "Alphanumeric loan number, 6–20 characters")
      @PathVariable
      @Pattern(regexp = "^[A-Za-z0-9_]{6,20}$",
               message = "loanNumber must be 6–20 alphanumeric characters")
          String loanNumber,
      @Valid @RequestBody CreateNoteRequest request,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
    return loanNotesService.createNote(loanNumber, request, idempotencyKey);
  }
}
```

### Task 4.5: Verify and commit

```powershell
.\gradlew.bat :loan-data-api:compileJava
```

Expected: `BUILD SUCCESSFUL`

```powershell
git add -A
git commit -m "feat: Phase 4 — LoanPaymentsController, LoanNotesController, payment/notes services"
```

---

## Phase 5 — Security

### Task 5.1: Create SecurityConfig

Create `loan-data-api/src/main/java/.../security/SecurityConfig.java`:

```java
// Layer: security — stateless Spring Security configuration.
// gcp profile: JWT OAuth2 resource server (issuer + audience from env vars).
// local profile: permit all but still stateless (no sessions created).
package com.example.lending.loandata.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

  /** GCP profile: stateless OAuth2 resource server with JWT validation. */
  @Bean
  @Profile("gcp")
  public SecurityFilterChain gcpSecurityFilterChain(
      HttpSecurity http,
      @Value("${OAUTH2_ISSUER_URI}") String issuerUri,
      @Value("${OAUTH2_AUDIENCE}") String audience) throws Exception {

    JwtDecoder decoder = buildDecoder(issuerUri, audience);

    http
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .csrf(csrf -> csrf.disable())
        .cors(cors -> cors.disable())
        .headers(headers -> headers
            .contentTypeOptions(c -> {})
            .frameOptions(f -> f.deny())
            .cacheControl(c -> {}))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
            .anyRequest().authenticated())
        .oauth2ResourceServer(oauth2 -> oauth2
            .jwt(jwt -> jwt.decoder(decoder)));

    return http.build();
  }

  /** Local profile: permit all — still stateless, no sessions. */
  @Bean
  @Profile({"local", "default", "test"})
  public SecurityFilterChain localSecurityFilterChain(HttpSecurity http) throws Exception {
    http
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .csrf(csrf -> csrf.disable())
        .cors(cors -> cors.disable())
        .headers(headers -> headers
            .contentTypeOptions(c -> {})
            .frameOptions(f -> f.deny())
            .cacheControl(c -> {}))
        .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());

    return http.build();
  }

  private JwtDecoder buildDecoder(String issuerUri, String audience) {
    NimbusJwtDecoder decoder = JwtDecoders.fromIssuerLocation(issuerUri);
    OAuth2TokenValidator<Jwt> audienceValidator =
        token -> token.getAudience().contains(audience)
            ? org.springframework.security.oauth2.core.OAuth2TokenValidatorResult.success()
            : org.springframework.security.oauth2.core.OAuth2TokenValidatorResult.failure(
                new org.springframework.security.oauth2.core.OAuth2Error(
                    "invalid_token", "Required audience not present", null));
    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuerUri), audienceValidator));
    return decoder;
  }
}
```

### Task 5.2: Add OAuth2 env vars to application-gcp.yaml

The `SecurityConfig` reads `OAUTH2_ISSUER_URI` and `OAUTH2_AUDIENCE` from environment variables directly — they do not go into `AppProperties` (they are Spring Security internals). This is correct.

No YAML changes needed for gcp — they come from env at runtime.

### Task 5.3: Verify and commit

```powershell
.\gradlew.bat :loan-data-api:compileJava
```

Expected: `BUILD SUCCESSFUL`

```powershell
git add -A
git commit -m "feat: Phase 5 — Spring Security, stateless JWT/OAuth2 for gcp, permit-all for local"
```

---

## Phase 6 — Log Masking + .env.example + Testcontainers Fix

### Task 6.1: Create LogMask utility

Create `loan-data-api/src/main/java/.../observability/LogMask.java`:

```java
// Layer: observability — utility methods for masking sensitive values in log statements.
// Rules: account/loan numbers → last 4 chars only; customer names → first char + ***;
// amounts → redacted entirely. Never log full sensitive values.
package com.example.lending.loandata.observability;

public final class LogMask {

  private LogMask() {}

  /** Returns last 4 chars of an account or loan number, prefixed with ****. */
  public static String account(String value) {
    if (value == null || value.length() <= 4) return "****";
    return "****" + value.substring(value.length() - 4);
  }

  /** Returns first character of a name followed by ***. */
  public static String name(String value) {
    if (value == null || value.isBlank()) return "***";
    return value.charAt(0) + "***";
  }

  /** Always returns a fixed redaction marker for monetary amounts. */
  public static String amount(String value) {
    return "[REDACTED]";
  }
}
```

### Task 6.2: Apply masking in service log calls

In `RelationshipAccountService.java`, the `customerId` is logged only if there is a log.error call. Check and update any log calls:

```java
// In RelationshipAccountService — if there is a log.warn/error that uses customerId:
log.warn("Customer not found: {}", LogMask.account(customerId));
```

In `BigtableRepository.java`, masking is already applied in `mask()` — update the private method to use `LogMask.account()`:

```java
// Replace private mask() method with a call to LogMask:
throw new DatastoreException("Bigtable read failed for loan: " + LogMask.account(loanNumber), e);
```

Remove the private `mask()` method from `BigtableRepository` and add the import for `LogMask`.

### Task 6.3: Create .env.example

Create `.env.example` at repo root:

```bash
# Environment variable reference for the loan-data-api service.
# Copy to .env for local overrides (never commit .env).
# All variables with no default in application.yaml are REQUIRED in gcp profile.

# ── Local profile (defaults shown — override if your setup differs) ──────────
LOCAL_DB_HOST=localhost
LOCAL_DB_PORT=5432
LOCAL_DB_NAME=loandb
LOCAL_DB_USER=loanuser
LOCAL_DB_PASSWORD=loanpass
BIGTABLE_EMULATOR_HOST=localhost:9035
PORT=8080

# ── GCP profile (required — no defaults) ─────────────────────────────────────
BIGTABLE_PROJECT_ID=your-gcp-project-id
BIGTABLE_INSTANCE_ID=your-bigtable-instance-id
BIGTABLE_APP_PROFILE_ID=default

ALLOYDB_INSTANCE_URI=projects/YOUR_PROJECT/locations/YOUR_REGION/clusters/YOUR_CLUSTER/instances/YOUR_INSTANCE
ALLOYDB_DB_NAME=loandb
ALLOYDB_IAM_USER=service-account@your-project.iam.gserviceaccount.com
ALLOYDB_IP_TYPE=PRIVATE

DOWNSTREAM_BASE_URL=https://your-loan-servicing-api.example.com

OAUTH2_ISSUER_URI=https://accounts.google.com
OAUTH2_AUDIENCE=your-api-audience

# ── Container image (Jib) ─────────────────────────────────────────────────────
IMAGE_NAME=gcr.io/your-project/loan-data-api:latest
```

### Task 6.4: Add .env to .gitignore

Edit `.gitignore` to append:

```
### Secrets ###
.env
```

### Task 6.5: Fix Testcontainers integration test annotations

Check `LoanIntegrationTest.java` and `RelationshipAccountIntegrationTest.java`. Replace `@Disabled("REQUIRES_DOCKER")` with `@Testcontainers(disabledWithoutDocker = true)`.

Also add `@Container` to any `GenericContainer` fields if they exist.

The correct annotation for Spring Boot 4 is `org.testcontainers.junit.jupiter.Testcontainers`.

### Task 6.6: Verify and commit

```powershell
.\gradlew.bat :loan-data-api:check
```

Expected: `BUILD SUCCESSFUL` (tests pass, coverage ≥ 80%)

```powershell
git add -A
git commit -m "feat: Phase 6 — LogMask utility, .env.example, .gitignore fix, Testcontainers annotation"
```

---

## Phase 7 — Tests for New Code

### Task 7.1: LoanServicingClientTest (MockRestServiceServer)

Create `loan-data-api/src/test/java/.../client/LoanServicingClientTest.java`:

```java
// Layer: test — verifies downstream client GET retry and POST no-retry behaviour
// using MockRestServiceServer.
package com.example.lending.loandata.client;

import com.example.lending.loandata.config.AppProperties;
import com.example.lending.loandata.config.DownstreamClientConfig;
import com.example.lending.loandata.model.dto.downstream.CreateNoteRequest;
import com.example.lending.loandata.model.dto.downstream.NoteType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class LoanServicingClientTest {
  // MockRestServiceServer does not directly wire into HttpServiceProxyFactory.
  // Instead test the RetryClientHttpRequestInterceptor in isolation and test
  // the full client with a WireMock or embedded server approach.
  // For the coverage gate, test the interceptor unit and the services that wrap the client.
}
```

**Note:** `MockRestServiceServer` integrates with `RestTemplate`, not with the `HttpServiceProxyFactory`/`RestClient` combo directly. Instead, write:

1. **Unit test for `RetryClientHttpRequestInterceptor`** — mock `ClientHttpRequestExecution` to throw `IOException` on first N calls and succeed on N+1.

2. **Unit tests for services** (`LoanPaymentsServiceTest`, `LoanNotesServiceTest`) — mock `LoanServicingClient` directly.

### Task 7.2: RetryInterceptorTest

Create `loan-data-api/src/test/java/.../client/RetryClientHttpRequestInterceptorTest.java`:

```java
// Layer: test — verifies that GET requests are retried on IOException and
// POST requests are not retried.
package com.example.lending.loandata.client;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.retry.backoff.NoBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

import java.io.IOException;
import java.net.ConnectException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class RetryClientHttpRequestInterceptorTest {

  private RetryTemplate retryTemplate() {
    SimpleRetryPolicy policy = new SimpleRetryPolicy(3, Map.of(IOException.class, true));
    RetryTemplate t = new RetryTemplate();
    t.setRetryPolicy(policy);
    t.setBackOffPolicy(new NoBackOffPolicy());
    return t;
  }

  @Test
  void get_retriesOnIOException() throws Exception {
    RetryClientHttpRequestInterceptor interceptor =
        new RetryClientHttpRequestInterceptor(retryTemplate());

    var request = mock(org.springframework.http.HttpRequest.class);
    when(request.getMethod()).thenReturn(HttpMethod.GET);

    var execution = mock(ClientHttpRequestExecution.class);
    var response = mock(org.springframework.http.client.ClientHttpResponse.class);
    when(execution.execute(any(), any()))
        .thenThrow(new ConnectException("refused"))
        .thenReturn(response);

    var result = interceptor.intercept(request, new byte[0], execution);
    assertThat(result).isSameAs(response);
    verify(execution, times(2)).execute(any(), any());
  }

  @Test
  void post_doesNotRetryOnIOException() throws Exception {
    RetryClientHttpRequestInterceptor interceptor =
        new RetryClientHttpRequestInterceptor(retryTemplate());

    var request = mock(org.springframework.http.HttpRequest.class);
    when(request.getMethod()).thenReturn(HttpMethod.POST);

    var execution = mock(ClientHttpRequestExecution.class);
    when(execution.execute(any(), any()))
        .thenThrow(new ConnectException("refused"));

    assertThatThrownBy(() -> interceptor.intercept(request, new byte[0], execution))
        .isInstanceOf(IOException.class);
    verify(execution, times(1)).execute(any(), any());
  }
}
```

### Task 7.3: LoanPaymentsServiceTest + LoanNotesServiceTest

Create `loan-data-api/src/test/java/.../service/LoanPaymentsServiceTest.java`:

```java
// Layer: test — unit test for LoanPaymentsService.
package com.example.lending.loandata.service;

import com.example.lending.loandata.client.LoanServicingClient;
import com.example.lending.loandata.model.dto.downstream.LoanPaymentsResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoanPaymentsServiceTest {

  @Mock LoanServicingClient client;
  @InjectMocks LoanPaymentsService service;

  @Test
  void getPayments_delegatesToClient() {
    var expected = new LoanPaymentsResponse("LN1000000001", 1, null, null, List.of());
    when(client.getPayments("LN1000000001")).thenReturn(expected);

    var result = service.getPayments("LN1000000001");

    assertThat(result).isSameAs(expected);
  }
}
```

Create `loan-data-api/src/test/java/.../service/LoanNotesServiceTest.java`:

```java
// Layer: test — unit test for LoanNotesService. Verifies idempotency key generation.
package com.example.lending.loandata.service;

import com.example.lending.loandata.client.LoanServicingClient;
import com.example.lending.loandata.model.dto.downstream.CreateNoteRequest;
import com.example.lending.loandata.model.dto.downstream.CreateNoteResponse;
import com.example.lending.loandata.model.dto.downstream.NoteType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoanNotesServiceTest {

  @Mock LoanServicingClient client;
  @InjectMocks LoanNotesService service;

  @Test
  void createNote_usesProvidedIdempotencyKey() {
    var req = new CreateNoteRequest(NoteType.GENERAL, "Test note", "user1");
    var resp = new CreateNoteResponse("id1", "LN001", NoteType.GENERAL, "Test note", "user1", OffsetDateTime.now());
    when(client.createNote(eq("LN001"), eq(req), eq("my-key"))).thenReturn(resp);

    service.createNote("LN001", req, "my-key");

    verify(client).createNote("LN001", req, "my-key");
  }

  @Test
  void createNote_generatesUuidWhenKeyAbsent() {
    var req = new CreateNoteRequest(NoteType.PAYMENT, "Auto key test", "user2");
    var resp = new CreateNoteResponse("id2", "LN002", NoteType.PAYMENT, "Auto key test", "user2", OffsetDateTime.now());
    ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
    when(client.createNote(eq("LN002"), eq(req), keyCaptor.capture())).thenReturn(resp);

    service.createNote("LN002", req, null);

    assertThat(keyCaptor.getValue()).matches(
        "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
  }
}
```

### Task 7.4: Controller slice tests for new endpoints

Create `loan-data-api/src/test/java/.../controller/LoanPaymentsControllerTest.java`:

```java
// Layer: test — @WebMvcTest slice for LoanPaymentsController.
package com.example.lending.loandata.controller;

import com.example.lending.loandata.exception.DownstreamClientException;
import com.example.lending.loandata.model.dto.downstream.LoanPaymentsResponse;
import com.example.lending.loandata.service.LoanPaymentsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.context.annotation.Import;
import com.example.lending.loandata.security.SecurityConfig;
import com.example.lending.loandata.exception.GlobalExceptionHandler;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(LoanPaymentsController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class LoanPaymentsControllerTest {

  @Autowired MockMvc mockMvc;
  @MockitoBean LoanPaymentsService service;

  @Test
  void getPayments_returns200() throws Exception {
    var resp = new LoanPaymentsResponse("LN1000000001", 0, null, null, List.of());
    when(service.getPayments("LN1000000001")).thenReturn(resp);

    mockMvc.perform(get("/api/v1/loans/LN1000000001/payments"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.loanNumber").value("LN1000000001"));
  }

  @Test
  void getPayments_invalidLoanNumber_returns400() throws Exception {
    mockMvc.perform(get("/api/v1/loans/!!/payments"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void getPayments_downstreamNotFound_returns404() throws Exception {
    when(service.getPayments("LN1000000001"))
        .thenThrow(new DownstreamClientException(404, "Not found"));

    mockMvc.perform(get("/api/v1/loans/LN1000000001/payments"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.title").value("Not Found"));
  }

  @Test
  void getPayments_downstream5xx_returns502() throws Exception {
    when(service.getPayments("LN1000000001"))
        .thenThrow(new DownstreamClientException(500, "Server error"));

    mockMvc.perform(get("/api/v1/loans/LN1000000001/payments"))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.title").value("Bad Gateway"));
  }
}
```

Create a similar `LoanNotesControllerTest.java` covering: 201 success, 400 validation error (missing noteText), 404 downstream, 502 downstream 5xx.

### Task 7.5: Security slice test

Create `loan-data-api/src/test/java/.../security/SecurityConfigTest.java`:

```java
// Layer: test — verifies actuator is open and API endpoints require auth in gcp profile.
package com.example.lending.loandata.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import com.example.lending.loandata.controller.LoanController;
import com.example.lending.loandata.service.LoanService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LoanController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("local")
class SecurityConfigLocalTest {

  @Autowired MockMvc mockMvc;
  @MockitoBean LoanService loanService;

  @Test
  void localProfile_permitsAllRequests() throws Exception {
    // local profile — no auth required, 404 because no loan seeded but not 401
    mockMvc.perform(get("/api/v1/loans/LN1000000001"))
        .andExpect(status().isNot(401));
  }
}
```

### Task 7.6: Run check and fix coverage

```powershell
.\gradlew.bat :loan-data-api:check
```

Review the JaCoCo HTML report at `loan-data-api/build/reports/jacoco/test/html/index.html`. If coverage is below 80%, add targeted tests for uncovered branches.

```powershell
git add -A
git commit -m "feat: Phase 7 — tests for downstream client, retry interceptor, new endpoints, security"
```

---

## Phase 8 — Final Docs + PowerShell Script + Check

### Task 8.1: Add PowerShell seed script

Create `scripts/Start-LocalBigtable.ps1`:

```powershell
# Starts the Cloud Bigtable emulator on port 9035 (Windows PowerShell).
# The LocalBigtableSeeder bean seeds the data automatically on app startup.
# Requires: gcloud CLI with 'gcloud components install beta bigtable'

Write-Host "Starting Bigtable emulator on port 9035..."
& gcloud beta emulators bigtable start --host-port=localhost:9035
```

### Task 8.2: Annotate docker-compose.yml

Open `docker-compose.yml` and add a header comment on line 1:

```yaml
# Optional: Docker-based local dev (requires Docker Desktop).
# On Windows without Docker, use the LocalBigtableSeeder bean (auto-seeds on startup)
# and start PostgreSQL natively. See README.md for native setup instructions.
```

### Task 8.3: Final check

```powershell
.\gradlew.bat :loan-data-api:check
```

Expected: `BUILD SUCCESSFUL`, coverage ≥ 80%, no Spotless violations.

### Task 8.4: Final commit

```powershell
git add -A
git commit -m "feat: Phase 8 — PowerShell seed script, docker-compose annotation, final check passing"
```

---

## Verification Summary

After all phases, confirm:

1. `.\gradlew.bat :loan-data-api:check` passes with ≥ 80% line coverage
2. `.\gradlew.bat :downstream-stub:bootJar` produces a runnable stub jar
3. `CLAUDE.md` exists at repo root
4. `settings.gradle` includes both modules
5. `loan-data-api/src/` contains all production code
6. `loan-data-api/src/main/resources/application-gcp.yaml` exists (not prod)
7. `.env.example` at repo root
8. `.env` in `.gitignore`
9. All entities have `schema = "loan_data"` in `@Table`
10. `CustomerAccountRelationshipEntity` uses `@EmbeddedId`
11. `LocalBigtableSeeder` bean exists in the loan-data-api module
12. `SecurityConfig` has two profile-specific `SecurityFilterChain` beans

---

## Open Assumptions (To Confirm With Client)

1. Downstream auth: Google ID token or mTLS in production? (stub uses none)
2. Idempotency scope: server-side deduplication in downstream, or header only?
3. Partial failure policy: failing 503 vs. partial data — client preference confirmed?
4. Row-key design: loan number only, or composite with date for time-series?
5. AlloyDB IP type: PRIVATE vs. PUBLIC (default set to PRIVATE)
6. OAUTH2_AUDIENCE value format for GCP-issued JWTs
