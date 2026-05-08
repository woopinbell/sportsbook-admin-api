package com.sportsbook.admin.security;

import com.sportsbook.admin.error.ProblemDetailSupport;
import java.security.interfaces.RSAPublicKey;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * Operator security (ADR-0011): a stateless RS256 bearer-token resource server, an IP allowlist,
 * and role-based method guards.
 *
 * <p>Pipeline for {@code /admin/**}: {@link IpAllowlistFilter} (CIDR check) → {@link
 * BearerTokenAuthenticationFilter} (verify the RS256 signature + expiry via {@link #jwtDecoder},
 * map the {@code role} claim to a {@code ROLE_*} authority) → {@code @PreAuthorize} on the
 * controller method. Failures render as RFC 7807 (ADR-0004): no/invalid/expired token → 401,
 * insufficient role → 403, disallowed IP → 403.
 *
 * <p>Health / readiness probes and the Prometheus scrape stay open ({@code permitAll}) so k8s and
 * the metrics collector reach them without a token; everything else not explicitly matched is
 * {@code denyAll}. CSRF is disabled because there is no session/cookie — every call carries a
 * bearer token (ADR-0011).
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

  private final AdminSecurityProperties properties;

  public SecurityConfig(AdminSecurityProperties properties) {
    this.properties = properties;
  }

  @Bean
  public SecurityFilterChain securityFilterChain(
      HttpSecurity http, JwtDecoder jwtDecoder, ProblemDetailSupport problems) throws Exception {
    IpAllowlistFilter ipAllowlistFilter = new IpAllowlistFilter(properties.ipAllowlist(), problems);
    AuthenticationEntryPoint entryPoint = unauthorizedEntryPoint(problems);
    AccessDeniedHandler accessDeniedHandler = forbiddenHandler(problems);

    http.csrf(csrf -> csrf.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/actuator/health/**", "/actuator/health")
                    .permitAll()
                    .requestMatchers("/actuator/prometheus", "/actuator/info")
                    .permitAll()
                    .requestMatchers("/admin/**")
                    .authenticated()
                    .anyRequest()
                    .denyAll())
        .oauth2ResourceServer(
            oauth ->
                oauth
                    .jwt(jwt -> jwt.decoder(jwtDecoder).jwtAuthenticationConverter(roleConverter()))
                    .authenticationEntryPoint(entryPoint))
        .exceptionHandling(
            ex -> ex.authenticationEntryPoint(entryPoint).accessDeniedHandler(accessDeniedHandler))
        .addFilterBefore(ipAllowlistFilter, BearerTokenAuthenticationFilter.class);

    return http.build();
  }

  /**
   * RS256 decoder built from the configured public key (ADR-0011). The default validator already
   * enforces {@code exp} / {@code nbf}; when an issuer is configured it is additionally pinned.
   */
  @Bean
  public JwtDecoder jwtDecoder() {
    RSAPublicKey publicKey = RsaKeys.parsePublicKey(properties.jwt().publicKey());
    NimbusJwtDecoder decoder =
        NimbusJwtDecoder.withPublicKey(publicKey)
            .signatureAlgorithm(SignatureAlgorithm.RS256)
            .build();
    String issuer = properties.jwt().issuer();
    if (issuer != null && !issuer.isBlank()) {
      decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
    }
    return decoder;
  }

  /** Maps the single {@code role} claim to one {@code ROLE_<NAME>} authority (ADR-0011). */
  private JwtAuthenticationConverter roleConverter() {
    String roleClaim = properties.jwt().roleClaim();
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(
        jwt ->
            AdminRole.fromClaim(jwt.getClaimAsString(roleClaim))
                .map(
                    role -> List.<GrantedAuthority>of(new SimpleGrantedAuthority(role.authority())))
                .orElseGet(List::of));
    return converter;
  }

  private static AuthenticationEntryPoint unauthorizedEntryPoint(ProblemDetailSupport problems) {
    return (request, response, ex) ->
        problems.write(
            response,
            HttpStatus.UNAUTHORIZED,
            ProblemDetailSupport.UNAUTHORIZED,
            "Unauthorized",
            "UNAUTHORIZED",
            "a valid operator bearer token is required",
            request);
  }

  private static AccessDeniedHandler forbiddenHandler(ProblemDetailSupport problems) {
    return (request, response, ex) ->
        problems.write(
            response,
            HttpStatus.FORBIDDEN,
            ProblemDetailSupport.FORBIDDEN,
            "Forbidden",
            "FORBIDDEN",
            "the operator role does not permit this operation",
            request);
  }
}
