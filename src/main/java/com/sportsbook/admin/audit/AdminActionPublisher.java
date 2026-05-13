package com.sportsbook.admin.audit;

import com.sportsbook.admin.event.AdminActionRecorded;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes the Avro-encoded {@link AdminActionRecorded} to the {@code admin.action} topic
 * (ADR-0011 dual-record). Keyed by actor id so one operator's actions keep partition order.
 */
@Component
public class AdminActionPublisher {

  private final KafkaTemplate<String, byte[]> kafka;
  private final String topic;

  public AdminActionPublisher(
      KafkaTemplate<String, byte[]> auditKafkaTemplate,
      @Value("${admin.audit.topic}") String topic) {
    this.kafka = auditKafkaTemplate;
    this.topic = topic;
  }

  public void publish(String actorKey, AdminActionRecorded event) {
    kafka.send(topic, actorKey, AvroSerializer.toBytes(event));
  }
}
