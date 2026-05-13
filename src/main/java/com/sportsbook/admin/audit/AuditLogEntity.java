package com.sportsbook.admin.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * The {@code audit_log} row — admin-api's durable copy of an operator action (ADR-0011). The same
 * record is published to Kafka {@code admin.action}; the action id ties the two copies together for
 * cross-verification. No {@code @Data} / generated equals — entity identity is the {@code actionId}
 * primary key only.
 */
@Entity
@Table(name = "audit_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditLogEntity {

  @Id
  @Column(name = "action_id")
  private UUID actionId;

  @Column(name = "actor_id", nullable = false)
  private String actorId;

  @Column(name = "actor_role", nullable = false)
  private String actorRole;

  @Column(nullable = false)
  private String action;

  @Column private String target;

  @Column(nullable = false)
  private String outcome;

  @Column(name = "http_status", nullable = false)
  private int httpStatus;

  @Column private String reason;

  @Column(name = "trace_id")
  private String traceId;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;

  // Eleven columns of a flat audit record; a builder would not add clarity here.
  @SuppressWarnings("checkstyle:ParameterNumber")
  public AuditLogEntity(
      UUID actionId,
      String actorId,
      String actorRole,
      String action,
      String target,
      String outcome,
      int httpStatus,
      String reason,
      String traceId,
      Instant occurredAt) {
    this.actionId = actionId;
    this.actorId = actorId;
    this.actorRole = actorRole;
    this.action = action;
    this.target = target;
    this.outcome = outcome;
    this.httpStatus = httpStatus;
    this.reason = reason;
    this.traceId = traceId;
    this.occurredAt = occurredAt;
  }
}
