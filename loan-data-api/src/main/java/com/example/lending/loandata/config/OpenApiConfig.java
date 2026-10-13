package com.example.lending.loandata.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures the springdoc-openapi bean for this application. The spec is intentionally kept at
 * OpenAPI 3.0 (not 3.1) because GCP API Gateway only accepts 3.0 documents.
 */
@Configuration
public class OpenApiConfig {

  @Bean
  public OpenAPI openApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("Loan Data API")
                .version("v1")
                .description(
                    "Read-only REST API for loan details (Cloud Bigtable) and customer"
                        + " relationship accounts (AlloyDB). Deployed on GCP Cloud Run behind"
                        + " API Gateway."))
        .addSecurityItem(new SecurityRequirement().addList("ApiKeyAuth"))
        .components(
            new Components()
                .addSecuritySchemes(
                    "ApiKeyAuth",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.HEADER)
                        .name("x-api-key")
                        .description("API key issued by GCP API Gateway")));
  }
}
