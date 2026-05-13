package com.sportsbook.admin.audit;

import com.sportsbook.admin.context.AdminContext;
import com.sportsbook.admin.event.AdminActionRecorded;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Dual-records an operator action (ADR-0011): a durable row in {@code audit_log} and a streaming
 * copy on Kafka {@code admin.action}, tied together by the action id for cross-verification.
 *
 * <p>V1 tradeoff: the downstream operation has already taken effect by the time we record
 * (admin-api is not transactional with the owning service), so an audit-store failure is logged and
 * metered ({@code admin.audit.write.failure}) rather than propagated — failing the operator's
 * response would falsely report the action itself as failed. Writing the DB copy first keeps the
 * durable record even if Kafka is unavailable.
 */
@Service
public class AuditService {

  private static final Logger log = LoggerFactory.getLogger(AuditService.class);
  private static final String FAILURE_METRIC = "admin.audit.write.failure";

  private final AuditLogRepository repository;
  private final AdminActionPublisher publisher;
  private final MeterRegistry meters;

  public AuditService(
      AuditLogRepository repository, AdminActionPublisher publisher, MeterRegistry meters) {
    this.repository = repository;
    this.publisher = publisher;
    this.meters = meters;
  }

  @SuppressWarnings("checkstyle:ParameterNumber")
  public void record(
      AdminContext context,
      AdminAction action,
      String target,
      String reason,
      String outcome,
      int httpStatus) {
    Instant occurredAt = Instant.now();
    UUID actionId = context.actionId();
    String actorId = context.actor().id();
    String actorRole = context.actor().roleName();

    try {
      repository.save(
          new AuditLogEntity(
              actionId,
              actorId,
              actorRole,
              action.name(),
              target,
              outcome,
              httpStatus,
              reason,
              context.traceId(),
              occurredAt));
    } catch (RuntimeException e) {
      meters.counter(FAILURE_METRIC, "store", "db").increment();
      log.error("audit DB write failed for action {} ({})", actionId, action, e);
    }

    try {
      AdminActionRecorded event =
          AdminActionRecorded.newBuilder()
              .setActionId(actionId.toString())
              .setActorId(actorId)
              .setActorRole(actorRole)
              .setAction(action.name())
              .setTarget(target)
              .setOutcome(outcome)
              .setHttpStatus(httpStatus)
              .setReason(reason)
              .setTraceId(context.traceId())
              .setOccurredAt(occurredAt)
              .build();
      publisher.publish(actorId, event);
    } catch (RuntimeException e) {
      meters.counter(FAILURE_METRIC, "store", "kafka").increment();
      log.error("audit Kafka publish failed for action {} ({})", actionId, action, e);
    }
  }
}
