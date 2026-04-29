package com.sportsbook.admin.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Operator authn/authz configuration, bound from {@code admin.security.*} (ADR-0011).
 *
 * <p>admin-api verifies JWTs but never issues them — a separate IAM owns issuance; V1 supplies only
 * the RSA public key. The IP allowlist defaults to loopback + RFC 1918 private ranges so local dev
 * and in-cluster traffic pass out of the box; production locks it to the corp network / VPN.
 */
@ConfigurationProperties(prefix = "admin.security")
public record AdminSecurityProperties(Jwt jwt, List<String> ipAllowlist) {

  private static final List<String> DEFAULT_ALLOWLIST =
      List.of("127.0.0.1/32", "::1/128", "10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16");

  public AdminSecurityProperties {
    if (jwt == null) {
      jwt = new Jwt(null, null, null);
    }
    if (ipAllowlist == null || ipAllowlist.isEmpty()) {
      ipAllowlist = DEFAULT_ALLOWLIST;
    }
  }

  /** RS256 verification inputs. {@code issuer} is optional (validated only when set). */
  public record Jwt(String publicKey, String roleClaim, String issuer) {
    public Jwt {
      if (roleClaim == null || roleClaim.isBlank()) {
        roleClaim = "role";
      }
    }
  }
}
