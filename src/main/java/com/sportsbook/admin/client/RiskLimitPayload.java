package com.sportsbook.admin.client;

import com.sportsbook.protocol.value.Currency;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Body of risk-service {@code PATCH /internal/v1/risk/limits/{userId}}, used directly as the
 * admin-api request body and forwarded as-is (admin-api is a thin proxy). {@code limitType} is the
 * string name of risk's {@code LimitType} enum (kept as a String so admin-api need not depend on
 * the risk module); {@code currency} is nullable for the currency-agnostic SELECTIONS_PER_MINUTE
 * limit.
 */
public record RiskLimitPayload(
    @NotBlank String limitType, Currency currency, @PositiveOrZero long amount) {}
