// Test layer: smoke test that verifies the full application context loads successfully.
package com.example.lending.loandata;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Full application-context smoke test. Requires a live PostgreSQL container and the Bigtable
 * emulator, both provided via Docker / Testcontainers. Skipped automatically when Docker is
 * unavailable.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class LoanDataApiApplicationTests {

  @Test
  void contextLoads() {}
}
