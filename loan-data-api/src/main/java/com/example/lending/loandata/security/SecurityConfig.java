// Security layer: configures stateless Spring Security for gcp (JWT RS) and local (permit all)
// profiles.
package com.example.lending.loandata.security;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

  // ── GCP profile: JWT OAuth2 Resource Server ───────────────────────────────

  @Bean
  @Profile("gcp")
  public SecurityFilterChain gcpSecurityFilterChain(HttpSecurity http) throws Exception {
    http.sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .csrf(csrf -> csrf.disable())
        .cors(cors -> cors.disable())
        .headers(
            headers ->
                headers
                    .contentTypeOptions(ct -> {})
                    .frameOptions(fo -> fo.deny())
                    .cacheControl(cc -> {}))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/actuator/health/**")
                    .permitAll()
                    .requestMatchers("/actuator/info")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(rs -> rs.jwt(jwt -> {}));
    return http.build();
  }

  @Bean
  @Profile("gcp")
  public JwtDecoder jwtDecoder(
      @Value("${OAUTH2_ISSUER_URI}") String issuerUri,
      @Value("${OAUTH2_AUDIENCE}") String audience) {
    NimbusJwtDecoder decoder = JwtDecoders.fromIssuerLocation(issuerUri);
    // Validate audience claim to prevent token reuse from other services.
    OAuth2TokenValidator<Jwt> audienceValidator =
        new JwtClaimValidator<List<String>>(
            JwtClaimNames.AUD, aud -> aud != null && aud.contains(audience));
    OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(issuerUri);
    OAuth2TokenValidator<Jwt> combined =
        new DelegatingOAuth2TokenValidator<>(withIssuer, audienceValidator);
    decoder.setJwtValidator(combined);
    return decoder;
  }

  // ── Local profile: permit all (still stateless, still sets security headers) ─

  @Bean
  @Profile("local")
  public SecurityFilterChain localSecurityFilterChain(HttpSecurity http) throws Exception {
    http.sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .csrf(csrf -> csrf.disable())
        .cors(cors -> cors.disable())
        .headers(
            headers ->
                headers
                    .contentTypeOptions(ct -> {})
                    .frameOptions(fo -> fo.deny())
                    .cacheControl(cc -> {}))
        .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
    return http.build();
  }
}
