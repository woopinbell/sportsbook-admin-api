package com.sportsbook.admin.security;

import java.util.Locale;
import java.util.Optional;

/**
 * Operator roles (ADR-0011). The JWT carries a single {@code role} claim; admin-api maps it to a
 * Spring authority {@code ROLE_<NAME>} and guards each endpoint with {@code @PreAuthorize}.
 *
 * <ul>
 *   <li>{@link #ADMIN} — everything.
 *   <li>{@link #TRADER} — settlement (void / replay) + market (close / reopen).
 *   <li>{@link #CS} — wallet refunds + reads.
 *   <li>{@link #READONLY} — audit-log reads only.
 * </ul>
 */
public enum AdminRole {
  ADMIN,
  TRADER,
  CS,
  READONLY;

  /** Spring Security authority name for this role ({@code hasRole('TRADER')} matches this). */
  public String authority() {
    return "ROLE_" + name();
  }

  /**
   * Parses the {@code role} claim case-insensitively (the ADR-0011 example uses lowercase {@code
   * role: trader}). Returns empty for a missing or unrecognized value rather than throwing — an
   * authenticated token with no recognized role simply gets no authority and fails every role
   * guard.
   */
  public static Optional<AdminRole> fromClaim(String claim) {
    if (claim == null || claim.isBlank()) {
      return Optional.empty();
    }
    try {
      return Optional.of(valueOf(claim.trim().toUpperCase(Locale.ROOT)));
    } catch (IllegalArgumentException unknownRole) {
      return Optional.empty();
    }
  }
}
