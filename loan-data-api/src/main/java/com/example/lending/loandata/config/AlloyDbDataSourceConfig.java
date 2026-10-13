// Config layer: configures AlloyDB (gcp profile) and local Postgres (local profile) DataSource
// beans.
package com.example.lending.loandata.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * DataSource configuration. Separated into two profile-specific inner classes so the correct bean
 * is created without conditional logic in a single class.
 *
 * <ul>
 *   <li><b>gcp profile:</b> AlloyDB Java Connector with IAM database authentication — no passwords,
 *       the service account identity is used as the DB user.
 *   <li><b>local / test profiles:</b> Plain PostgreSQL JDBC; the AlloyDB connector and IAM are
 *       bypassed for developer convenience.
 * </ul>
 */
@Configuration
public class AlloyDbDataSourceConfig {

  /** GCP DataSource — AlloyDB connector with IAM auth. Passwords are never used. */
  @Configuration
  @Profile("gcp")
  static class ProdDataSource {

    @Bean
    DataSource dataSource(
        @Value("${app.datasource.alloydb.instance-uri}") String instanceUri,
        @Value("${app.datasource.alloydb.database}") String database,
        @Value("${app.datasource.alloydb.iam-user}") String iamUser,
        @Value("${app.datasource.alloydb.ip-type:PRIVATE}") String ipType,
        @Value("${spring.datasource.hikari.maximum-pool-size:10}") int maxPoolSize,
        @Value("${spring.datasource.hikari.minimum-idle:2}") int minIdle,
        @Value("${spring.datasource.hikari.connection-timeout:5000}") long connectionTimeout,
        @Value("${spring.datasource.hikari.max-lifetime:1800000}") long maxLifetime) {

      HikariConfig config = new HikariConfig();
      config.setDriverClassName("org.postgresql.Driver");
      // AlloyDB connector is loaded via the socket factory; no TCP host/port needed.
      config.setJdbcUrl(
          "jdbc:postgresql:///"
              + database
              + "?socketFactory=com.google.cloud.alloydb.SocketFactory"
              + "&alloydbInstanceName="
              + instanceUri
              + "&alloydbEnableIAMAuth=true"
              + "&alloydbIpType="
              + ipType
              + "&currentSchema=loan_data");
      config.setUsername(iamUser);
      config.setMaximumPoolSize(maxPoolSize);
      config.setMinimumIdle(minIdle);
      config.setConnectionTimeout(connectionTimeout);
      config.setMaxLifetime(maxLifetime);
      return new HikariDataSource(config);
    }
  }

  /** Local / test DataSource — plain PostgreSQL; configured entirely from Spring properties. */
  @Configuration
  @Profile({"local", "test", "default"})
  static class LocalDataSource {
    // Spring Boot's DataSource autoconfiguration handles spring.datasource.* in these profiles.
    // No manual bean needed; keeping this class to document the profile split explicitly.
  }
}
