package com.sportsbook.admin.client;

import com.sportsbook.admin.context.AdminContext;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Delegates the two settlement operator actions to settlement-service (ADR-0011): manually void a
 * bet and replay an event's settlement. admin-api adds no logic — it forwards with the admin
 * context headers and relays settlement's status (the bodiless 200 / 202; 404 / 409 surface as a
 * {@link com.sportsbook.admin.error.DownstreamStatusException}).
 */
@Component
public class SettlementClient {

  private final RestClient http;

  public SettlementClient(RestClient settlementRestClient) {
    this.http = settlementRestClient;
  }

  public ResponseEntity<Void> voidBet(UUID betId, AdminContext context) {
    return DownstreamCalls.execute(
        "settlement-service",
        () ->
            http.post()
                .uri("/internal/v1/settlements/void/{betId}", betId)
                .headers(headers -> AdminHeaders.apply(headers, context))
                .retrieve()
                .toBodilessEntity());
  }

  public ResponseEntity<Void> replay(UUID eventId, AdminContext context) {
    return DownstreamCalls.execute(
        "settlement-service",
        () ->
            http.post()
                .uri("/internal/v1/settlements/replay/{eventId}", eventId)
                .headers(headers -> AdminHeaders.apply(headers, context))
                .retrieve()
                .toBodilessEntity());
  }
}
