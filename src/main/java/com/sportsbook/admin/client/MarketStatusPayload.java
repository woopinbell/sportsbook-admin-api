package com.sportsbook.admin.client;

import jakarta.validation.constraints.NotBlank;

/**
 * Body of odds-feed-service {@code POST /internal/v1/events/{eventId}/markets/{marketId}/{close|
 * reopen}}, used directly as the admin-api request body and forwarded as-is. The {@code reason} is
 * required — a market state change must be explained for the audit trail.
 */
public record MarketStatusPayload(@NotBlank String reason) {}
