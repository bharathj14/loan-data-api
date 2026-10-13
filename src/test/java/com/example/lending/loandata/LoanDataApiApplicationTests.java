package com.example.lending.loandata;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Full application-context smoke test. Requires a live PostgreSQL container and the Bigtable
 * emulator, both provided via Docker / Testcontainers.
 *
 * <p>REQUIRES_DOCKER: start Docker Desktop, then re-enable this test.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Disabled("REQUIRES_DOCKER: full context load needs PostgreSQL + Bigtable emulator containers")
class LoanDataApiApplicationTests {

  @Test
  void contextLoads() {}
}
