// Config layer: Spring beans for Bigtable data and (local-only) admin clients.
package com.example.lending.loandata.config;

import com.google.api.gax.retrying.RetrySettings;
import com.google.cloud.bigtable.admin.v2.BigtableTableAdminClient;
import com.google.cloud.bigtable.admin.v2.BigtableTableAdminSettings;
import com.google.cloud.bigtable.data.v2.BigtableDataClient;
import com.google.cloud.bigtable.data.v2.BigtableDataSettings;
import java.io.IOException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.threeten.bp.Duration;

/**
 * Produces the single {@link BigtableDataClient} bean. The client is closed gracefully when the
 * application context shuts down via {@code destroyMethod}.
 *
 * <p>When {@code app.bigtable.emulatorHost} is set (local profile), the client connects to a local
 * emulator and skips Application Default Credentials. In all other profiles the client uses ADC.
 *
 * <p>A {@link BigtableTableAdminClient} bean is additionally registered for the {@code local}
 * profile only, so {@link LocalBigtableSeeder} can ensure the emulator table exists at startup.
 */
@Configuration
public class BigtableConfig {

  @Bean(destroyMethod = "close")
  public BigtableDataClient bigtableDataClient(AppProperties properties) throws IOException {
    AppProperties.Bigtable cfg = properties.bigtable();
    Duration timeout = Duration.ofMillis(cfg.readTimeoutMs());

    if (cfg.emulatorHost() != null && !cfg.emulatorHost().isBlank()) {
      // Local/emulator path — parse "host:port"
      String[] parts = cfg.emulatorHost().split(":");
      String host = parts[0];
      int port = Integer.parseInt(parts[1]);
      BigtableDataSettings settings =
          BigtableDataSettings.newBuilderForEmulator(host, port)
              .setProjectId(cfg.projectId())
              .setInstanceId(cfg.instanceId())
              .build();
      return BigtableDataClient.create(settings);
    }

    // Production path — uses Application Default Credentials
    BigtableDataSettings.Builder builder =
        BigtableDataSettings.newBuilder()
            .setProjectId(cfg.projectId())
            .setInstanceId(cfg.instanceId())
            .setAppProfileId(cfg.appProfileId());

    // Apply read timeout to both single-row and streaming-read operations
    RetrySettings timeoutOnly =
        builder.stubSettings().readRowSettings().getRetrySettings().toBuilder()
            .setTotalTimeout(timeout)
            .build();
    builder.stubSettings().readRowSettings().setRetrySettings(timeoutOnly);

    RetrySettings streamingTimeout =
        builder.stubSettings().readRowsSettings().getRetrySettings().toBuilder()
            .setTotalTimeout(timeout)
            .build();
    builder.stubSettings().readRowsSettings().setRetrySettings(streamingTimeout);

    return BigtableDataClient.create(builder.build());
  }

  /** Admin client used only by {@link LocalBigtableSeeder} to create tables in the emulator. */
  @Bean(destroyMethod = "close")
  @Profile("local")
  public BigtableTableAdminClient bigtableTableAdminClient(AppProperties properties)
      throws IOException {
    AppProperties.Bigtable cfg = properties.bigtable();
    // Always emulator when local profile is active — parse "host:port"
    String[] parts = cfg.emulatorHost().split(":");
    String host = parts[0];
    int port = Integer.parseInt(parts[1]);
    BigtableTableAdminSettings settings =
        BigtableTableAdminSettings.newBuilderForEmulator(host, port)
            .setProjectId(cfg.projectId())
            .setInstanceId(cfg.instanceId())
            .build();
    return BigtableTableAdminClient.create(settings);
  }
}
