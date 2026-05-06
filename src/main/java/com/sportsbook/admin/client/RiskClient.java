package com.sportsbook.admin.client;

import com.sportsbook.admin.context.AdminContext;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Delegates a per-user limit change to risk-service (ADR-0011) — {@code PATCH
 * /internal/v1/risk/limits/{userId}}. The validated payload is forwarded unchanged; risk relays a
 * bodiless 204 on success.
 */
@Component
public class RiskClient {

  private final RestClient http;

  public RiskClient(RestClient riskRestClient) {
    this.http = riskRestClient;
  }

  public ResponseEntity<Void> updateLimits(
      String userId, RiskLimitPayload payload, AdminContext context) {
    return DownstreamCalls.execute(
        "risk-service",
        () ->
            http.patch()
                .uri("/internal/v1/risk/limits/{userId}", userId)
                .headers(headers -> AdminHeaders.apply(headers, context))
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .toBodilessEntity());
  }
}
