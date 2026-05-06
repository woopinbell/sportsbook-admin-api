package com.sportsbook.admin.api;

import java.util.UUID;

/**
 * Response of a refund: wallet's {@code operationGroupId} (for ledger cross-reference) and the
 * admin {@code actionId} (the audit-trail / idempotency key for this operation).
 */
public record RefundResponse(UUID operationGroupId, UUID actionId) {}
