package com.sportsbook.admin.api;

import com.sportsbook.admin.client.SettlementClient;
import com.sportsbook.admin.context.AdminContext;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Settlement operator actions (ADR-0011), delegated to settlement-service. ADMIN or TRADER only.
 * admin-api adds no logic; it relays settlement's status, and the audit aspect records the action.
 */
@RestController
@RequestMapping("/admin/v1/settlements")
public class SettlementAdminController {

  private final SettlementClient settlementClient;

  public SettlementAdminController(SettlementClient settlementClient) {
    this.settlementClient = settlementClient;
  }

  /** Manually void a bet and refund its stake. */
  @PostMapping("/{betId}/void")
  @PreAuthorize("hasAnyRole('ADMIN','TRADER')")
  public ResponseEntity<Void> voidBet(@PathVariable UUID betId, AdminContext context) {
    ResponseEntity<Void> downstream = settlementClient.voidBet(betId, context);
    return ResponseEntity.status(downstream.getStatusCode()).build();
  }

  /** Replay an event's settlement (e.g. after a DLQ drain or a late correction). */
  @PostMapping("/replay/{eventId}")
  @PreAuthorize("hasAnyRole('ADMIN','TRADER')")
  public ResponseEntity<Void> replay(@PathVariable UUID eventId, AdminContext context) {
    ResponseEntity<Void> downstream = settlementClient.replay(eventId, context);
    return ResponseEntity.status(downstream.getStatusCode()).build();
  }
}
