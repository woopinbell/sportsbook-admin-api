package com.sportsbook.admin.api;

import com.sportsbook.admin.client.MarketClient;
import com.sportsbook.admin.client.MarketStatusPayload;
import com.sportsbook.admin.context.AdminContext;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Market operator actions (ADR-0011): force-close or reopen a market, delegated to
 * odds-feed-service. ADMIN or TRADER only. The path carries both eventId and marketId because
 * odds-feed scopes a market by its event.
 */
@RestController
@RequestMapping("/admin/v1/events/{eventId}/markets/{marketId}")
public class MarketAdminController {

  private final MarketClient marketClient;

  public MarketAdminController(MarketClient marketClient) {
    this.marketClient = marketClient;
  }

  @PostMapping("/close")
  @PreAuthorize("hasAnyRole('ADMIN','TRADER')")
  public ResponseEntity<Void> close(
      @PathVariable UUID eventId,
      @PathVariable UUID marketId,
      @Valid @RequestBody MarketStatusPayload payload,
      AdminContext context) {
    ResponseEntity<Void> downstream = marketClient.close(eventId, marketId, payload, context);
    return ResponseEntity.status(downstream.getStatusCode()).build();
  }

  @PostMapping("/reopen")
  @PreAuthorize("hasAnyRole('ADMIN','TRADER')")
  public ResponseEntity<Void> reopen(
      @PathVariable UUID eventId,
      @PathVariable UUID marketId,
      @Valid @RequestBody MarketStatusPayload payload,
      AdminContext context) {
    ResponseEntity<Void> downstream = marketClient.reopen(eventId, marketId, payload, context);
    return ResponseEntity.status(downstream.getStatusCode()).build();
  }
}
