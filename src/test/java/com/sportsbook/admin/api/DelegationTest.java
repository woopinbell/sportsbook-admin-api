package com.sportsbook.admin.api;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.http.Fault;
import com.sportsbook.admin.audit.AdminActionPublisher;
import com.sportsbook.admin.audit.AuditLogRepository;
import com.sportsbook.admin.client.AdminHeaders;
import com.sportsbook.admin.support.TestKeys;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Delegation + admin-context propagation proof (ADR-0011). One WireMock instance stands in for
 * every downstream {@code /internal/v1} service (their paths don't collide); admin-api's four
 * downstream base URLs all point at it. Each test asserts that admin-api forwards to the correct
 * path with the X-Admin-Actor-Id / -Role / -Action-Id headers, relays the downstream status, and
 * maps transport failures to 502 / 504.
 */
@SpringBootTest(
    properties = {
      "spring.autoconfigure.exclude="
          + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
          + "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration,"
          + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
          + "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration,"
          + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration,"
          + "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration",
      "admin.downstream.read-timeout=600ms"
    })
@AutoConfigureMockMvc
class DelegationTest {

  private static final WireMockServer WM =
      new WireMockServer(
          com.github.tomakehurst.wiremock.core.WireMockConfiguration.options().dynamicPort());

  static {
    WM.start();
  }

  @DynamicPropertySource
  static void downstream(DynamicPropertyRegistry registry) {
    String base = "http://localhost:" + WM.port();
    registry.add("admin.downstream.settlement-base-url", () -> base);
    registry.add("admin.downstream.wallet-base-url", () -> base);
    registry.add("admin.downstream.risk-base-url", () -> base);
    registry.add("admin.downstream.odds-feed-base-url", () -> base);
    registry.add("admin.security.jwt.public-key", TestKeys::trustedPublicKeyPem);
  }

  @AfterAll
  static void stopWireMock() {
    WM.stop();
  }

  @BeforeEach
  void resetWireMock() {
    WM.resetAll();
  }

  @Autowired private MockMvc mvc;

  // Audit dual-write needs a DB + Kafka, both excluded here — mock it out; this
  // slice proves delegation. AuditIntegrityTest covers the real dual-write.
  @MockBean private AuditLogRepository auditLogRepository;
  @MockBean private AdminActionPublisher adminActionPublisher;

  // ----- settlement void: delegate + propagate headers + relay 200 -----

  @Test
  void voidDelegatesToSettlementWithAdminContextHeaders() throws Exception {
    UUID betId = UUID.randomUUID();
    String path = "/internal/v1/settlements/void/" + betId;
    WM.stubFor(
        WireMock.post(WireMock.urlEqualTo(path)).willReturn(WireMock.aResponse().withStatus(200)));

    mvc.perform(
            post("/admin/v1/settlements/{betId}/void", betId)
                .header(AUTHORIZATION, bearer("u-admin-1", "ADMIN")))
        .andExpect(status().isOk());

    WM.verify(
        WireMock.postRequestedFor(WireMock.urlEqualTo(path))
            .withHeader(AdminHeaders.ACTOR_ID, WireMock.equalTo("u-admin-1"))
            .withHeader(AdminHeaders.ACTOR_ROLE, WireMock.equalTo("ADMIN"))
            .withHeader(AdminHeaders.ACTION_ID, WireMock.matching("[0-9a-fA-F-]{36}")));
  }

  // ----- settlement replay: relay 202 -----

  @Test
  void replayDelegatesAndRelays202() throws Exception {
    UUID eventId = UUID.randomUUID();
    String path = "/internal/v1/settlements/replay/" + eventId;
    WM.stubFor(
        WireMock.post(WireMock.urlEqualTo(path)).willReturn(WireMock.aResponse().withStatus(202)));

    mvc.perform(
            post("/admin/v1/settlements/replay/{eventId}", eventId)
                .header(AUTHORIZATION, bearer("u-trader-1", "TRADER")))
        .andExpect(status().isAccepted());
  }

  // ----- wallet refund: body mapping (HOUSE_POOL), idempotency key, response -----

  @Test
  void refundDelegatesToWalletCreditWithIdempotencyKey() throws Exception {
    UUID userId = UUID.randomUUID();
    UUID groupId = UUID.randomUUID();
    String path = "/internal/v1/wallet/transactions/credit";
    WM.stubFor(
        WireMock.post(WireMock.urlEqualTo(path))
            .willReturn(WireMock.okJson("{\"operationGroupId\":\"" + groupId + "\"}")));

    mvc.perform(
            post("/admin/v1/wallet/{userId}/refund", userId)
                .header(AUTHORIZATION, bearer("u-cs-1", "CS"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":10000,\"currency\":\"KRW\",\"reason\":\"goodwill\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.operationGroupId").value(groupId.toString()))
        .andExpect(jsonPath("$.actionId").isNotEmpty());

    WM.verify(
        WireMock.postRequestedFor(WireMock.urlEqualTo(path))
            .withHeader("Idempotency-Key", WireMock.matching("[0-9a-fA-F-]{36}"))
            .withHeader(AdminHeaders.ACTOR_ID, WireMock.equalTo("u-cs-1"))
            .withRequestBody(
                WireMock.matchingJsonPath("$.userId", WireMock.equalTo(userId.toString())))
            .withRequestBody(WireMock.matchingJsonPath("$.source", WireMock.equalTo("HOUSE_POOL")))
            .withRequestBody(
                WireMock.matchingJsonPath("$.amount.currency", WireMock.equalTo("KRW"))));
  }

  // ----- risk limits: forward body, relay 204 -----

  @Test
  void limitUpdateDelegatesToRiskAndRelays204() throws Exception {
    String userId = "u-bettor-9";
    String path = "/internal/v1/risk/limits/" + userId;
    WM.stubFor(
        WireMock.patch(WireMock.urlEqualTo(path)).willReturn(WireMock.aResponse().withStatus(204)));

    mvc.perform(
            patch("/admin/v1/risk/users/{userId}/limits", userId)
                .header(AUTHORIZATION, bearer("u-trader-1", "TRADER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"limitType\":\"DAILY_STAKE\",\"currency\":\"KRW\",\"amount\":500000}"))
        .andExpect(status().isNoContent());

    WM.verify(
        WireMock.patchRequestedFor(WireMock.urlEqualTo(path))
            .withHeader(AdminHeaders.ACTION_ID, WireMock.matching("[0-9a-fA-F-]{36}"))
            .withRequestBody(
                WireMock.matchingJsonPath("$.limitType", WireMock.equalTo("DAILY_STAKE"))));
  }

  // ----- market close: path carries eventId + marketId, relay 202 -----

  @Test
  void marketCloseDelegatesToOddsFeed() throws Exception {
    UUID eventId = UUID.randomUUID();
    UUID marketId = UUID.randomUUID();
    String path = "/internal/v1/events/" + eventId + "/markets/" + marketId + "/close";
    WM.stubFor(
        WireMock.post(WireMock.urlEqualTo(path)).willReturn(WireMock.aResponse().withStatus(202)));

    mvc.perform(
            post("/admin/v1/events/{eventId}/markets/{marketId}/close", eventId, marketId)
                .header(AUTHORIZATION, bearer("u-trader-1", "TRADER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"suspected pricing error\"}"))
        .andExpect(status().isAccepted());

    WM.verify(
        WireMock.postRequestedFor(WireMock.urlEqualTo(path))
            .withRequestBody(
                WireMock.matchingJsonPath(
                    "$.reason", WireMock.equalTo("suspected pricing error"))));
  }

  // ----- role matrix (insufficient role never reaches the downstream) -----

  @Test
  void csCannotVoidSettlement() throws Exception {
    mvc.perform(
            post("/admin/v1/settlements/{betId}/void", UUID.randomUUID())
                .header(AUTHORIZATION, bearer("u-cs-1", "CS")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    WM.verify(0, WireMock.postRequestedFor(WireMock.urlPathMatching("/internal/.*")));
  }

  @Test
  void traderCannotRefund() throws Exception {
    mvc.perform(
            post("/admin/v1/wallet/{userId}/refund", UUID.randomUUID())
                .header(AUTHORIZATION, bearer("u-trader-1", "TRADER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":10000,\"currency\":\"KRW\",\"reason\":\"x\"}"))
        .andExpect(status().isForbidden());
    WM.verify(0, WireMock.postRequestedFor(WireMock.urlPathMatching("/internal/.*")));
  }

  // ----- downstream error relaying -----

  @Test
  void downstream404IsRelayedVerbatim() throws Exception {
    UUID betId = UUID.randomUUID();
    String path = "/internal/v1/settlements/void/" + betId;
    WM.stubFor(
        WireMock.post(WireMock.urlEqualTo(path))
            .willReturn(
                WireMock.aResponse()
                    .withStatus(404)
                    .withHeader("Content-Type", "application/problem+json")
                    .withBody(
                        "{\"errorCode\":\"NOT_FOUND\",\"title\":\"Not found\",\"status\":404}")));

    mvc.perform(
            post("/admin/v1/settlements/{betId}/void", betId)
                .header(AUTHORIZATION, bearer("u-admin-1", "ADMIN")))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
  }

  @Test
  void downstreamConnectionFailureBecomes502() throws Exception {
    UUID betId = UUID.randomUUID();
    WM.stubFor(
        WireMock.post(WireMock.urlEqualTo("/internal/v1/settlements/void/" + betId))
            .willReturn(WireMock.aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

    mvc.perform(
            post("/admin/v1/settlements/{betId}/void", betId)
                .header(AUTHORIZATION, bearer("u-admin-1", "ADMIN")))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.errorCode").value("BAD_GATEWAY"));
  }

  @Test
  void downstreamReadTimeoutBecomes504() throws Exception {
    UUID eventId = UUID.randomUUID();
    WM.stubFor(
        WireMock.post(WireMock.urlEqualTo("/internal/v1/settlements/replay/" + eventId))
            .willReturn(WireMock.aResponse().withStatus(202).withFixedDelay(1500)));

    mvc.perform(
            post("/admin/v1/settlements/replay/{eventId}", eventId)
                .header(AUTHORIZATION, bearer("u-admin-1", "ADMIN")))
        .andExpect(status().isGatewayTimeout())
        .andExpect(jsonPath("$.errorCode").value("GATEWAY_TIMEOUT"));
  }

  private static String bearer(String subject, String role) {
    return "Bearer " + TestKeys.validToken(subject, role);
  }
}
