package com.example.lending.loandata.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Strongly-typed, fail-fast configuration bound from {@code app.*} properties. Validation runs at
 * startup; a missing or invalid value prevents the application from starting.
 */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(@Valid @NotNull Bigtable bigtable, @Valid @NotNull Executor executor) {

  /** Bigtable connection properties. */
  public record Bigtable(
      @NotBlank String projectId,
      @NotBlank String instanceId,
      @NotBlank String appProfileId,
      /**
       * Maximum wall-clock time for a single Bigtable row read. Must be below API-GW 15 s limit.
       */
      @Min(500) @Max(14000) long readTimeoutMs,
      /**
       * Optional: {@code host:port} of a local Bigtable emulator. When set the client connects to
       * the emulator and does not use Application Default Credentials. Local profile only.
       */
      String emulatorHost) {}

  /** Thread-pool settings for the bounded executor used to fan-out Bigtable calls in parallel. */
  public record Executor(
      @Positive int corePoolSize,
      @Positive int maxPoolSize,
      @Min(0) int queueCapacity,
      /** Prefix added to thread names; useful in log correlation. */
      @NotBlank String threadNamePrefix) {}
}
