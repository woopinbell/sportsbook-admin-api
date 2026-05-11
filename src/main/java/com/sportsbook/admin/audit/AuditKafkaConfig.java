package com.sportsbook.admin.audit;

import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

/**
 * Producer for the {@code admin.action} audit topic. The wire shape is {@code (String key, byte[]
 * value)} — the value is the Avro-encoded {@code AdminActionRecorded} (no Schema Registry in V1,
 * ADR-0014). Idempotent + {@code acks=all} so a retry never duplicates an audit record. Defining
 * these beans makes Spring Boot back off its default String-valued template.
 */
@Configuration
public class AuditKafkaConfig {

  // Confluent-recommended ceiling with enable.idempotence=true.
  private static final int MAX_IN_FLIGHT_REQUESTS = 5;

  @Value("${spring.kafka.bootstrap-servers}")
  private String bootstrapServers;

  @Bean
  public ProducerFactory<String, byte[]> auditProducerFactory() {
    Map<String, Object> props = new HashMap<>();
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
    props.put(ProducerConfig.ACKS_CONFIG, "all");
    props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
    props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, MAX_IN_FLIGHT_REQUESTS);
    props.put(ProducerConfig.CLIENT_ID_CONFIG, "admin-api-audit");
    return new DefaultKafkaProducerFactory<>(props);
  }

  @Bean
  public KafkaTemplate<String, byte[]> auditKafkaTemplate(
      ProducerFactory<String, byte[]> auditProducerFactory) {
    return new KafkaTemplate<>(auditProducerFactory);
  }
}
