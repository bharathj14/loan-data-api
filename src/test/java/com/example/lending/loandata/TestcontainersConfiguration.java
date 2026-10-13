package com.example.lending.loandata;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Shared Testcontainers configuration for integration tests. Spins up a PostgreSQL 16 container and
 * wires it into Spring's DataSource via {@code @ServiceConnection}.
 *
 * <p>REQUIRES_DOCKER — only imported by tests annotated
 * {@code @Import(TestcontainersConfiguration.class)}.
 */
@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

  @Bean
  @ServiceConnection
  PostgreSQLContainer postgresContainer() {
    return new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));
  }
}
