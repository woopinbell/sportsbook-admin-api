package com.sportsbook.admin.security;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Parses the operator JWT verification key. admin-api only ever verifies (never signs), so it loads
 * a single RSA public key from a PEM string supplied via env var (ADR-0011). A missing key is a
 * hard startup failure — admin-api must never run with authentication effectively disabled.
 */
public final class RsaKeys {

  private RsaKeys() {}

  /**
   * Parses a PEM-encoded {@code SubjectPublicKeyInfo} (the {@code -----BEGIN PUBLIC KEY-----}
   * block) into an {@link RSAPublicKey}.
   */
  public static RSAPublicKey parsePublicKey(String pem) {
    if (pem == null || pem.isBlank()) {
      throw new IllegalStateException(
          "admin.security.jwt.public-key (env ADMIN_JWT_PUBLIC_KEY) is required — "
              + "admin-api refuses to start without an operator JWT verification key");
    }
    String base64 =
        pem.replaceAll("-----BEGIN (.*)-----", "")
            .replaceAll("-----END (.*)-----", "")
            .replaceAll("\\s", "");
    try {
      byte[] der = Base64.getDecoder().decode(base64);
      KeyFactory factory = KeyFactory.getInstance("RSA");
      return (RSAPublicKey) factory.generatePublic(new X509EncodedKeySpec(der));
    } catch (GeneralSecurityException | IllegalArgumentException e) {
      throw new IllegalStateException(
          "admin.security.jwt.public-key is not a valid RSA public key", e);
    }
  }
}
