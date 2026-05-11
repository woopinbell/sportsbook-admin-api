package com.sportsbook.admin.client;

import com.sportsbook.admin.context.AdminContext;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Delegates force-close / reopen of a market to odds-feed-service (ADR-0011) — {@code POST
 * /internal/v1/events/{eventId}/markets/{marketId}/{close|reopen}}. odds-feed publishes a {@code
 * MarketStatusChanged} event and updates its cache; it relays a bodiless 202.
 */
@Component
public class MarketClient {

  private final RestClient http;

  public MarketClient(RestClient oddsFeedRestClient) {
    this.http = oddsFeedRestClient;
  }

  public ResponseEntity<Void> close(
      UUID eventId, UUID marketId, MarketStatusPayload payload, AdminContext context) {
    return transition("close", eventId, marketId, payload, context);
  }

  public ResponseEntity<Void> reopen(
      UUID eventId, UUID marketId, MarketStatusPayload payload, AdminContext context) {
    return transition("reopen", eventId, marketId, payload, context);
  }

  private ResponseEntity<Void> transition(
      String action,
      UUID eventId,
      UUID marketId,
      MarketStatusPayload payload,
      AdminContext context) {
    return DownstreamCalls.execute(
        "odds-feed-service",
        () ->
            http.post()
                .uri(
                    "/internal/v1/events/{eventId}/markets/{marketId}/{action}",
                    eventId,
                    marketId,
                    action)
                .headers(headers -> AdminHeaders.apply(headers, context))
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .toBodilessEntity());
  }
}
