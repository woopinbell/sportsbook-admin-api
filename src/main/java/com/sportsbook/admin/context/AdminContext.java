package com.sportsbook.admin.context;

import java.util.UUID;

/**
 * Per-action operator context, built once per request (ADR-0011). It bundles the authenticated
 * {@link AdminActor}, a freshly minted time-ordered {@code actionId} (UUID v7) that uniquely
 * identifies this operation, and the current trace id. The same {@code actionId} is propagated to
 * the downstream service (as {@code X-Admin-Action-Id} and, for wallet refunds, the {@code
 * Idempotency-Key}) and written to the audit trail, so one operator action is correlatable across
 * admin-api, the owning service, and the audit log.
 */
public record AdminContext(AdminActor actor, UUID actionId, String traceId) {}
