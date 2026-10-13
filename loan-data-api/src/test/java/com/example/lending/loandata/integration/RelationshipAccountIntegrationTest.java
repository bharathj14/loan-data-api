package com.example.lending.loandata.integration;

import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * End-to-end integration tests for the relationship-accounts endpoint.
 *
 * <p>REQUIRES_DOCKER: these tests start a PostgreSQL container (AlloyDB stand-in) seeded with
 * schema + data, and a Bigtable emulator container for loan enrichment. Enable by starting Docker
 * Desktop and removing {@code @Disabled}.
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
@Disabled("REQUIRES_DOCKER: start Docker Desktop and re-enable this class to run integration tests")
class RelationshipAccountIntegrationTest {

  @Test
  void getRelationshipAccounts_knownCustomer_returnsPaginatedAccounts() {
    fail("REQUIRES_DOCKER — enable once Docker Desktop is running");
  }

  @Test
  void getRelationshipAccounts_includeLoanDetails_attachesBigtableSummaries() {
    fail("REQUIRES_DOCKER — enable once Docker Desktop is running");
  }

  @Test
  void getRelationshipAccounts_unknownCustomer_returns404ProblemDetail() {
    fail("REQUIRES_DOCKER — enable once Docker Desktop is running");
  }

  @Test
  void getRelationshipAccounts_oversizedPage_returns400ProblemDetail() {
    fail("REQUIRES_DOCKER — enable once Docker Desktop is running");
  }

  @Test
  void getRelationshipAccounts_relationshipTypeFilter_returnsOnlyMatchingRows() {
    fail("REQUIRES_DOCKER — enable once Docker Desktop is running");
  }
}
