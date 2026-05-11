package com.sportsbook.admin.client;

import com.sportsbook.protocol.value.Money;
import java.util.UUID;

/**
 * Body of wallet-service {@code POST /internal/v1/wallet/transactions/credit}. admin-api cannot
 * import wallet's request type, so it mirrors the wire shape here. {@code source} is the string
 * name of wallet's {@code CreditCommand.Source}; admin refunds use {@code HOUSE_POOL} (the house
 * funds a manual refund — it does not assume a locked stake still exists).
 */
public record WalletCreditPayload(UUID userId, Money amount, String source) {

  public static final String ADMIN_REFUND_SOURCE = "HOUSE_POOL";

  public static WalletCreditPayload refund(UUID userId, Money amount) {
    return new WalletCreditPayload(userId, amount, ADMIN_REFUND_SOURCE);
  }
}
