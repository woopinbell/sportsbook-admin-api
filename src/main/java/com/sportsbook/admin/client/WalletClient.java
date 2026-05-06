package com.sportsbook.admin.client;

import com.sportsbook.admin.context.AdminContext;
import com.sportsbook.protocol.value.Money;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Delegates an operator refund to wallet-service (ADR-0011) as a credit ({@code POST
 * /internal/v1/wallet/transactions/credit}). The action id doubles as the {@code Idempotency-Key}
 * so a retried refund is a wallet-side no-op (ADR-0005). Returns wallet's operation group id for
 * the operator to cross-reference.
 */
@Component
public class WalletClient {

  private final RestClient http;

  public WalletClient(RestClient walletRestClient) {
    this.http = walletRestClient;
  }

  public UUID refund(UUID userId, Money amount, AdminContext context) {
    WalletOperationResponse response =
        DownstreamCalls.execute(
            "wallet-service",
            () ->
                http.post()
                    .uri("/internal/v1/wallet/transactions/credit")
                    .headers(headers -> AdminHeaders.apply(headers, context))
                    .header("Idempotency-Key", context.actionId().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(WalletCreditPayload.refund(userId, amount))
                    .retrieve()
                    .body(WalletOperationResponse.class));
    return response == null ? null : response.operationGroupId();
  }
}
