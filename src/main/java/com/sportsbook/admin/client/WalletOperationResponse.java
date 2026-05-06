package com.sportsbook.admin.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.UUID;

/**
 * Slice of wallet-service's transaction response that admin-api cares about — the operation group
 * id, echoed back to the operator for cross-referencing. Other fields (userId / amount / reason /
 * at) are ignored.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WalletOperationResponse(UUID operationGroupId) {}
