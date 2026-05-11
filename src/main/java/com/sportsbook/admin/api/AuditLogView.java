package com.sportsbook.admin.api;

import com.sportsbook.admin.audit.AuditLogEntity;
import java.time.Instant;
import java.util.UUID;

/** Wire shape of an audit-log entry. */
public record AuditLogView(
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

  public static AuditLogView from(AuditLogEntity e) {
    return new AuditLogView(
        e.getActionId(),
        e.getActorId(),
        e.getActorRole(),
        e.getAction(),
        e.getTarget(),
        e.getOutcome(),
        e.getHttpStatus(),
        e.getReason(),
        e.getTraceId(),
        e.getOccurredAt());
  }
}
