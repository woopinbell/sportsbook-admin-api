package com.sportsbook.admin.api;

import com.sportsbook.admin.audit.AdminAction;
import com.sportsbook.admin.audit.Audited;
import com.sportsbook.admin.client.WalletClient;
import com.sportsbook.admin.context.AdminContext;
import com.sportsbook.protocol.value.Money;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Wallet operator action (ADR-0011): refund a user, delegated to wallet-service as a credit. ADMIN
 * or CS only. The action id is the wallet idempotency key.
 */
@RestController
@RequestMapping("/admin/v1/wallet")
public class WalletAdminController {

  private final WalletClient walletClient;

  public WalletAdminController(WalletClient walletClient) {
    this.walletClient = walletClient;
  }

  @PostMapping("/{userId}/refund")
  @PreAuthorize("hasAnyRole('ADMIN','CS')")
  @Audited(value = AdminAction.WALLET_REFUND, target = "#userId", reason = "#request.reason()")
  public RefundResponse refund(
      @PathVariable UUID userId, @Valid @RequestBody RefundRequest request, AdminContext context) {
    Money amount = new Money(request.amount(), request.currency());
    UUID operationGroupId = walletClient.refund(userId, amount, context);
    return new RefundResponse(operationGroupId, context.actionId());
  }
}
