package com.sportsbook.admin.api;

import com.sportsbook.protocol.value.Currency;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Body of {@code POST /admin/v1/wallet/{userId}/refund}. Amount is flattened (minor units +
 * currency) for a clean admin wire shape; admin-api rebuilds {@code Money} before delegating.
 * {@code reason} is required and recorded in the audit trail (it is not forwarded to wallet —
 * wallet's credit has no reason field).
 */
public record RefundRequest(
    @Positive long amount, @NotNull Currency currency, @NotBlank String reason) {}
