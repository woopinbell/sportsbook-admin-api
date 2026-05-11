package com.sportsbook.admin.api;

import com.sportsbook.admin.client.RiskClient;
import com.sportsbook.admin.client.RiskLimitPayload;
import com.sportsbook.admin.context.AdminContext;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Risk operator action (ADR-0011): change a user's limit, delegated to risk-service. ADMIN or
 * TRADER only. The validated payload is forwarded unchanged.
 */
@RestController
@RequestMapping("/admin/v1/risk/users")
public class RiskAdminController {

  private final RiskClient riskClient;

  public RiskAdminController(RiskClient riskClient) {
    this.riskClient = riskClient;
  }

  @PatchMapping("/{userId}/limits")
  @PreAuthorize("hasAnyRole('ADMIN','TRADER')")
  public ResponseEntity<Void> updateLimits(
      @PathVariable String userId,
      @Valid @RequestBody RiskLimitPayload payload,
      AdminContext context) {
    ResponseEntity<Void> downstream = riskClient.updateLimits(userId, payload, context);
    return ResponseEntity.status(downstream.getStatusCode()).build();
  }
}
