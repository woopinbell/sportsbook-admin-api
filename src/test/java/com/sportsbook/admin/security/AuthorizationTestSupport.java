package com.sportsbook.admin.security;

import com.sportsbook.admin.support.TestKeys;

/** Shared authorization fixtures for tests that exercise the real security filter chain. */
public final class AuthorizationTestSupport {

  private AuthorizationTestSupport() {}

  public static String bearer(String token) {
    return "Bearer " + token;
  }

  public static String validBearer(String subject, String role) {
    return bearer(TestKeys.validToken(subject, role));
  }

  /**
   * Flips the first byte of the JWS signature so verification fails while the JWT stays
   * well-formed.
   */
  public static String tamperSignature(String token) {
    String[] parts = token.split("\\.");
    char first = parts[2].charAt(0);
    parts[2] = (first == 'A' ? 'B' : 'A') + parts[2].substring(1);
    return parts[0] + "." + parts[1] + "." + parts[2];
  }
}
