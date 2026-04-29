package com.sportsbook.admin.context;

import com.sportsbook.admin.security.AdminRole;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * The authenticated operator behind a request — the {@code sub} (actor id) and {@code role} from
 * the verified JWT. Carried into every downstream {@code /internal/v1} call as the {@code
 * X-Admin-Actor-Id} / {@code -Role} headers and written to the audit trail (ADR-0011), so an
 * operational incident can be traced to who did what.
 *
 * @param id the JWT subject (e.g. {@code u-admin-1234}); never null for an authenticated request
 * @param role parsed from the configured role claim; {@code null} if the claim is missing /
 *     unrecognized (such a token authenticates but satisfies no role guard)
 */
public record AdminActor(String id, AdminRole role) {

  /** Resolves the actor from a verified JWT, reading the role from {@code roleClaim}. */
  public static AdminActor from(Jwt jwt, String roleClaim) {
    return new AdminActor(
        jwt.getSubject(), AdminRole.fromClaim(jwt.getClaimAsString(roleClaim)).orElse(null));
  }

  /** Role name for header propagation / audit, or empty string when no role was present. */
  public String roleName() {
    return role == null ? "" : role.name();
  }
}
