package com.sportsbook.admin.audit;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence for the audit trail. */
public interface AuditLogRepository extends JpaRepository<AuditLogEntity, UUID> {

  /**
   * Time-window query with an optional actor filter (offset pagination, ADR-0004). A null {@code
   * actor} matches every operator; ordering is supplied by the {@link Pageable} (newest first).
   */
  @Query(
      "SELECT a FROM AuditLogEntity a "
          + "WHERE a.occurredAt >= :from AND a.occurredAt < :to "
          + "AND (:actor IS NULL OR a.actorId = :actor)")
  Page<AuditLogEntity> search(
      @Param("from") Instant from,
      @Param("to") Instant to,
      @Param("actor") String actor,
      Pageable pageable);
}
