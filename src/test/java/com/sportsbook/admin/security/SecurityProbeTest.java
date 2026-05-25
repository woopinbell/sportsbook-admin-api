package com.sportsbook.admin.security;

import static com.sportsbook.admin.security.AuthorizationTestSupport.bearer;
import static com.sportsbook.admin.security.AuthorizationTestSupport.validBearer;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sportsbook.admin.audit.AdminActionPublisher;
import com.sportsbook.admin.audit.AuditLogEntity;
import com.sportsbook.admin.audit.AuditLogRepository;
import com.sportsbook.admin.support.TestKeys;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Adversarial probes against the security-critical surface (ADR-0011 deliverable). Complements the
 * tampering / expiry / forged-key cases in {@link AdminSecurityTest} with the two attacks worth
 * proving explicitly: JWT algorithm confusion (an {@code alg=none} token and an HS256 token signed
 * with the RSA public key) must be rejected by the RS256-pinned decoder; and a SQL-injection string
 * in the only user-controlled query filter is neutralized by the parameterized JPQL query. Uses a
 * real PostgreSQL (Testcontainers) so the injection runs against an actual database.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class SecurityProbeTest {

  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("admin.security.jwt.public-key", TestKeys::trustedPublicKeyPem);
  }

  @Autowired private MockMvc mvc;
  @Autowired private AuditLogRepository auditLog;

  // No Kafka broker in this test; the read path under attack does not publish.
  @MockBean private AdminActionPublisher adminActionPublisher;

  @BeforeEach
  void seed() {
    auditLog.deleteAll();
    auditLog.save(
        new AuditLogEntity(
            UUID.randomUUID(),
            "u-real-operator",
            "ADMIN",
            "SETTLEMENT_VOID",
            "bet-1",
            "SUCCESS",
            200,
            null,
            null,
            Instant.now()));
  }

  @Test
  void sqlInjectionInActorFilterIsParameterizedAndMatchesNothing() throws Exception {
    // Sanity: the real actor filters to its one row.
    mvc.perform(
            get("/admin/v1/audit-logs")
                .param("actor", "u-real-operator")
                .header(AUTHORIZATION, validBearer("u-admin-1", "ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1));

    // The injection is bound as a literal value: it matches no actorId and does
    // not leak the seeded row (a concatenated query would have returned it).
    mvc.perform(
            get("/admin/v1/audit-logs")
                .param("actor", "' OR '1'='1")
                .header(AUTHORIZATION, validBearer("u-admin-1", "ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0));
  }

  @Test
  void algNoneTokenIsRejected() throws Exception {
    mvc.perform(
            get("/admin/v1/whoami")
                .header(AUTHORIZATION, bearer(TestKeys.noneAlgToken("u-attacker", "ADMIN"))))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
  }

  @Test
  void hmacAlgorithmConfusionTokenIsRejected() throws Exception {
    mvc.perform(
            get("/admin/v1/whoami")
                .header(
                    AUTHORIZATION, bearer(TestKeys.hmacWithPublicKeyToken("u-attacker", "ADMIN"))))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
  }

  @Test
  void requestFromDisallowedIpIsRejectedEvenWithValidToken() throws Exception {
    mvc.perform(
            get("/admin/v1/whoami")
                .header(AUTHORIZATION, validBearer("u-admin-1", "ADMIN"))
                .with(
                    request -> {
                      request.setRemoteAddr("203.0.113.7");
                      return request;
                    }))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.errorCode").value("IP_NOT_ALLOWED"));
  }
}
