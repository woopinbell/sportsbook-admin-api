package com.sportsbook.admin.api;

import com.sportsbook.admin.audit.AuditLogEntity;
import com.sportsbook.admin.audit.AuditLogRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Audit-log query (ADR-0011). Any authenticated operator may read — including READONLY, whose sole
 * purpose is auditing. Time-window + optional actor filter, offset-paginated newest-first
 * (ADR-0004). This is a read, so it is not itself audited.
 */
@RestController
@RequestMapping("/admin/v1/audit-logs")
public class AuditLogController {

  private static final int MAX_PAGE_SIZE = 200;
  private static final int DEFAULT_PAGE_SIZE = 20;

  private final AuditLogRepository repository;

  public AuditLogController(AuditLogRepository repository) {
    this.repository = repository;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('ADMIN','TRADER','CS','READONLY')")
  public OffsetPage<AuditLogView> search(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant to,
      @RequestParam(required = false) String actor,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    Instant fromTs = from != null ? from : Instant.EPOCH;
    Instant toTs = to != null ? to : Instant.now();
    int pageSize = Math.min(size <= 0 ? DEFAULT_PAGE_SIZE : size, MAX_PAGE_SIZE);
    Pageable pageable =
        PageRequest.of(Math.max(page, 0), pageSize, Sort.by(Sort.Direction.DESC, "occurredAt"));

    Page<AuditLogEntity> result = repository.search(fromTs, toTs, actor, pageable);
    List<AuditLogView> items = result.getContent().stream().map(AuditLogView::from).toList();
    return OffsetPage.from(result, items);
  }
}
