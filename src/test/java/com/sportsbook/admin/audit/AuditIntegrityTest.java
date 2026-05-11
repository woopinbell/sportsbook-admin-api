package com.sportsbook.admin.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.sportsbook.admin.event.AdminActionRecorded;
import com.sportsbook.admin.support.TestKeys;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.specific.SpecificDatumReader;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Audit-integrity proof (ADR-0011): every admin action lands identically in the {@code audit_log}
 * table (real PostgreSQL via Testcontainers) and the Kafka {@code admin.action} topic (embedded
 * broker), tied by the action id — the "dual-record, cross-verify" guarantee. Also proves a failed
 * downstream is recorded as FAILED and that the audit-log query returns what was written.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@EmbeddedKafka(
    topics = "admin.action",
    partitions = 1,
    bootstrapServersProperty = "spring.kafka.bootstrap-servers")
class AuditIntegrityTest {

  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  static WireMockServer wm = new WireMockServer(WireMockConfiguration.options().dynamicPort());

  static {
    wm.start();
  }

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    String base = "http://localhost:" + wm.port();
    registry.add("admin.downstream.settlement-base-url", () -> base);
    registry.add("admin.downstream.wallet-base-url", () -> base);
    registry.add("admin.downstream.risk-base-url", () -> base);
    registry.add("admin.downstream.odds-feed-base-url", () -> base);
    registry.add("admin.security.jwt.public-key", TestKeys::trustedPublicKeyPem);
  }

  @AfterAll
  static void stopWireMock() {
    wm.stop();
  }

  @Autowired private MockMvc mvc;
  @Autowired private AuditLogRepository auditLog;
  @Autowired private EmbeddedKafkaBroker broker;

  @BeforeEach
  void reset() {
    auditLog.deleteAll();
    wm.resetAll();
  }

  @Test
  void successfulActionIsDualRecordedAndCrossVerifies() throws Exception {
    UUID betId = UUID.randomUUID();
    wm.stubFor(
        WireMock.post(WireMock.urlEqualTo("/internal/v1/settlements/void/" + betId))
            .willReturn(WireMock.aResponse().withStatus(200)));

    mvc.perform(
            post("/admin/v1/settlements/{betId}/void", betId)
                .header(AUTHORIZATION, bearer("u-admin-1", "ADMIN")))
        .andExpect(status().isOk());

    // DB copy.
    List<AuditLogEntity> rows = auditLog.findAll();
    assertThat(rows).hasSize(1);
    AuditLogEntity row = rows.get(0);
    assertThat(row.getAction()).isEqualTo("SETTLEMENT_VOID");
    assertThat(row.getActorId()).isEqualTo("u-admin-1");
    assertThat(row.getActorRole()).isEqualTo("ADMIN");
    assertThat(row.getOutcome()).isEqualTo("SUCCESS");
    assertThat(row.getHttpStatus()).isEqualTo(200);
    assertThat(row.getTarget()).isEqualTo(betId.toString());

    // Kafka copy, matched by action id — the cross-verification.
    AdminActionRecorded event =
        findByActionId(row.getActionId().toString())
            .orElseThrow(() -> new AssertionError("no admin.action event for the recorded action"));
    assertThat(event.getAction()).hasToString("SETTLEMENT_VOID");
    assertThat(event.getActorId()).hasToString("u-admin-1");
    assertThat(event.getOutcome()).hasToString("SUCCESS");
    assertThat(event.getHttpStatus()).isEqualTo(200);
    assertThat(event.getTarget()).hasToString(betId.toString());
  }

  @Test
  void failedDownstreamIsRecordedAsFailed() throws Exception {
    UUID betId = UUID.randomUUID();
    wm.stubFor(
        WireMock.post(WireMock.urlEqualTo("/internal/v1/settlements/void/" + betId))
            .willReturn(WireMock.aResponse().withStatus(404)));

    mvc.perform(
            post("/admin/v1/settlements/{betId}/void", betId)
                .header(AUTHORIZATION, bearer("u-trader-1", "TRADER")))
        .andExpect(status().isNotFound());

    List<AuditLogEntity> rows = auditLog.findAll();
    assertThat(rows).hasSize(1);
    assertThat(rows.get(0).getOutcome()).isEqualTo("FAILED");
    assertThat(rows.get(0).getHttpStatus()).isEqualTo(404);
  }

  @Test
  void auditLogEndpointReturnsRecordedActionsNewestFirst() throws Exception {
    UUID firstBet = UUID.randomUUID();
    UUID secondBet = UUID.randomUUID();
    wm.stubFor(
        WireMock.post(WireMock.urlMatching("/internal/v1/settlements/void/.*"))
            .willReturn(WireMock.aResponse().withStatus(200)));

    mvc.perform(
            post("/admin/v1/settlements/{betId}/void", firstBet)
                .header(AUTHORIZATION, bearer("u-admin-1", "ADMIN")))
        .andExpect(status().isOk());
    mvc.perform(
            post("/admin/v1/settlements/{betId}/void", secondBet)
                .header(AUTHORIZATION, bearer("u-admin-1", "ADMIN")))
        .andExpect(status().isOk());

    mvc.perform(
            get("/admin/v1/audit-logs").header(AUTHORIZATION, bearer("u-readonly-1", "READONLY")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(jsonPath("$.items.length()").value(2))
        .andExpect(jsonPath("$.items[0].target").value(secondBet.toString()))
        .andExpect(jsonPath("$.items[1].target").value(firstBet.toString()));
  }

  private Optional<AdminActionRecorded> findByActionId(String actionId) {
    return drainAdminAction().stream()
        .filter(event -> actionId.contentEquals(event.getActionId()))
        .findFirst();
  }

  private List<AdminActionRecorded> drainAdminAction() {
    Map<String, Object> props =
        KafkaTestUtils.consumerProps("audit-it-" + UUID.randomUUID(), "true", broker);
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    List<AdminActionRecorded> events = new ArrayList<>();
    try (Consumer<String, byte[]> consumer =
        new DefaultKafkaConsumerFactory<String, byte[]>(props).createConsumer()) {
      consumer.subscribe(List.of("admin.action"));
      ConsumerRecords<String, byte[]> records =
          KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(5));
      for (ConsumerRecord<String, byte[]> record : records) {
        events.add(decode(record.value()));
      }
    }
    return events;
  }

  private static AdminActionRecorded decode(byte[] bytes) {
    try {
      SpecificDatumReader<AdminActionRecorded> reader =
          new SpecificDatumReader<>(AdminActionRecorded.class);
      return reader.read(null, DecoderFactory.get().binaryDecoder(bytes, null));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static String bearer(String subject, String role) {
    return "Bearer " + TestKeys.validToken(subject, role);
  }
}
