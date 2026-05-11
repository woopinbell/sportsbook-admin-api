package com.sportsbook.admin.audit;

/** The operator actions admin-api audits (ADR-0011). One value per delegated endpoint. */
public enum AdminAction {
  SETTLEMENT_VOID,
  SETTLEMENT_REPLAY,
  WALLET_REFUND,
  RISK_LIMIT_UPDATE,
  MARKET_CLOSE,
  MARKET_REOPEN
}
