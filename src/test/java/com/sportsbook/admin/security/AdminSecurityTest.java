package com.sportsbook.admin.security;

import static com.sportsbook.admin.security.AuthorizationTestSupport.bearer;
import static com.sportsbook.admin.security.AuthorizationTestSupport.tamperSignature;
import static com.sportsbook.admin.security.AuthorizationTestSupport.validBearer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sportsbook.admin.audit.AdminActionPublisher;
import com.sportsbook.admin.audit.AuditLogRepository;
import com.sportsbook.admin.support.TestKeys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Operator authn/authz proof (ADR-0011). Boots the real security filter chain (no DB / Kafka —
 * those auto-configurations are excluded) and drives it through MockMvc with RS256 tokens minted by
 * {@link TestKeys}: a valid token authenticates; a forged / expired / wrong-key / malformed token
 * is 401; an insufficient role is 403; a disallowed client IP is 403 — all rendered as RFC 7807.
 */
@SpringBootTest(
    properties = {
      "spring.autoconfigure.exclude="
          + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
          + "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration,"
          + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
          + "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration,"
          + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration,"
          + "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration"
    })
@AutoConfigureMockMvc
@ActiveProfiles("authz-test")
@Import(AdminSecurityTest.RoleProbeController.class)
class AdminSecurityTest {

  private static final String WHOAMI = "/admin/v1/whoami";
  private static final String ADMIN_ONLY = "/admin/v1/test/admin-only";

  @DynamicPropertySource
  static void verificationKey(DynamicPropertyRegistry registry) {
    registry.add("admin.security.jwt.public-key", TestKeys::trustedPublicKeyPem);
  }

  @Autowired private MockMvc mvc;

  // The audit layer needs a DB + Kafka, both excluded here — mock it out; this
  // slice proves auth, not auditing (AuditIntegrityTest covers the dual-write).
  @MockBean private AuditLogRepository auditLogRepository;
  @MockBean private AdminActionPublisher adminActionPublisher;

  @Test
  void validTokenAuthenticatesAndExposesActor() throws Exception {
    mvc.perform(get(WHOAMI).header(AUTHORIZATION, validBearer("u-admin-1", "ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value("u-admin-1"))
        .andExpect(jsonPath("$.role").value("ADMIN"));
  }

  @Test
  void missingTokenIsUnauthorized() throws Exception {
    mvc.perform(get(WHOAMI))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
  }

  @Test
  void expiredTokenIsUnauthorized() throws Exception {
    mvc.perform(
            get(WHOAMI).header(AUTHORIZATION, bearer(TestKeys.expiredToken("u-admin-1", "ADMIN"))))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
  }

  @Test
  void tokenSignedByUntrustedKeyIsUnauthorized() throws Exception {
    mvc.perform(
            get(WHOAMI).header(AUTHORIZATION, bearer(TestKeys.wrongKeyToken("u-admin-1", "ADMIN"))))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
  }

  @Test
  void tamperedSignatureIsUnauthorized() throws Exception {
    String valid = TestKeys.validToken("u-admin-1", "ADMIN");
    String tampered = tamperSignature(valid);
    String[] validSegments = valid.split("\\.", -1);
    String[] tamperedSegments = tampered.split("\\.", -1);

    assertEquals(3, validSegments.length);
    assertEquals(3, tamperedSegments.length);
    assertEquals(validSegments[0], tamperedSegments[0]);
    assertEquals(validSegments[1], tamperedSegments[1]);
    assertNotEquals(validSegments[2], tamperedSegments[2]);

    mvc.perform(get(WHOAMI).header(AUTHORIZATION, bearer(tampered)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
  }

  @Test
  void malformedTokenIsUnauthorized() throws Exception {
    mvc.perform(get(WHOAMI).header(AUTHORIZATION, "Bearer not-a-jwt"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
  }

  @Test
  void insufficientRoleIsForbidden() throws Exception {
    mvc.perform(get(ADMIN_ONLY).header(AUTHORIZATION, validBearer("u-trader-1", "TRADER")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
  }

  @Test
  void sufficientRoleIsAllowed() throws Exception {
    mvc.perform(get(ADMIN_ONLY).header(AUTHORIZATION, validBearer("u-admin-1", "ADMIN")))
        .andExpect(status().isOk());
  }

  @Test
  void disallowedClientIpIsForbidden() throws Exception {
    // Valid token, but the source IP is outside the allowlist — the IP filter
    // rejects it before authentication even runs.
    mvc.perform(
            get(WHOAMI)
                .header(AUTHORIZATION, validBearer("u-admin-1", "ADMIN"))
                .with(
                    request -> {
                      request.setRemoteAddr("8.8.8.8");
                      return request;
                    }))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.errorCode").value("IP_NOT_ALLOWED"));
  }

  /**
   * Minimal role-gated endpoint used only to prove the {@code @PreAuthorize} wiring
   * (profile-gated).
   */
  @Profile("authz-test")
  @RestController
  static class RoleProbeController {

    @GetMapping(ADMIN_ONLY)
    @PreAuthorize("hasRole('ADMIN')")
    String adminOnly() {
      return "ok";
    }
  }
}
