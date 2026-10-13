// Test layer: integration tests for the relationship-accounts endpoint — require
// Docker/Testcontainers.
package com.example.lending.loandata.integration;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * End-to-end integration tests for the relationship-accounts endpoint.
 *
 * <p>REQUIRES_DOCKER: these tests start a PostgreSQL container (AlloyDB stand-in) seeded with
 * schema + data, and a Bigtable emulator container for loan enrichment. Skipped automatically when
 * Docker is unavailable.
 *
 * <p>Test plan when enabled:
 *
 * <ul>
 *   <li>GET /api/v1/customers/{id}/relationship-accounts → 200, verify pagination and accounts.
 *   <li>GET with includeLoanDetails=true → verify loan summaries attached to LOAN accounts.
 *   <li>GET with non-existent customer → 404 ProblemDetail.
 *   <li>GET with size=200 → 400 ProblemDetail (exceeds max).
 *   <li>GET with relationshipType filter → only matching rows returned.
 * </ul>
 */
@Testcontainers(disabledWithoutDocker = true)
class RelationshipAccountIntegrationTest {

  @Test
  void getRelationshipAccounts_knownCustomer_returnsPaginatedAccounts() {
    assumeTrue(false, "REQUIRES_DOCKER — not yet implemented");
  }

  @Test
  void getRelationshipAccounts_includeLoanDetails_attachesBigtableSummaries() {
    assumeTrue(false, "REQUIRES_DOCKER — not yet implemented");
  }

  @Test
  void getRelationshipAccounts_unknownCustomer_returns404ProblemDetail() {
    assumeTrue(false, "REQUIRES_DOCKER — not yet implemented");
  }

  @Test
  void getRelationshipAccounts_oversizedPage_returns400ProblemDetail() {
    assumeTrue(false, "REQUIRES_DOCKER — not yet implemented");
  }

  @Test
  void getRelationshipAccounts_relationshipTypeFilter_returnsOnlyMatchingRows() {
    assumeTrue(false, "REQUIRES_DOCKER — not yet implemented");
  }
}
