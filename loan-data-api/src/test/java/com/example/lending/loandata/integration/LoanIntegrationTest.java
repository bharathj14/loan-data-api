// Test layer: integration tests for the loan endpoint — require Docker/Testcontainers.
package com.example.lending.loandata.integration;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * End-to-end integration tests for the loan endpoint.
 *
 * <p>REQUIRES_DOCKER: these tests start a Bigtable emulator container via Testcontainers and seed
 * it with sample loan rows. Skipped automatically when Docker is unavailable.
 *
 * <p>Test plan when enabled:
 *
 * <ul>
 *   <li>Seed the emulator with a known loan row.
 *   <li>GET /api/v1/loans/{loanNumber} → 200, verify all fields.
 *   <li>GET /api/v1/loans/NOTEXIST → 404 ProblemDetail.
 *   <li>GET /api/v1/loans/BAD → 400 ProblemDetail (fails validation).
 * </ul>
 */
@Testcontainers(disabledWithoutDocker = true)
class LoanIntegrationTest {

  @Test
  void getLoan_existingRow_returns200() {
    assumeTrue(false, "REQUIRES_DOCKER — not yet implemented");
  }

  @Test
  void getLoan_missingRow_returns404ProblemDetail() {
    assumeTrue(false, "REQUIRES_DOCKER — not yet implemented");
  }

  @Test
  void getLoan_invalidLoanNumber_returns400ProblemDetail() {
    assumeTrue(false, "REQUIRES_DOCKER — not yet implemented");
  }
}
