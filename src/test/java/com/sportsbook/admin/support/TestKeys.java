package com.sportsbook.admin.support;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

/**
 * Test-only RSA key material and JWT minting. Mirrors what a real IAM would do: sign operator
 * tokens with a private key whose public half admin-api is configured to trust. {@link #OTHER}
 * exists to prove a token signed by an untrusted key is rejected.
 */
public final class TestKeys {

  /**
   * The key pair admin-api trusts (its public key is fed to {@code admin.security.jwt.public-key}).
   */
  public static final KeyPair TRUSTED = generate();

  /** An untrusted key pair — tokens it signs must fail verification. */
  public static final KeyPair OTHER = generate();

  private static final int RSA_KEY_SIZE = 2048;
  private static final long DEFAULT_TTL_SECONDS = 300L;

  private TestKeys() {}

  private static KeyPair generate() {
    try {
      KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(RSA_KEY_SIZE);
      return generator.generateKeyPair();
    } catch (Exception e) {
      throw new IllegalStateException("RSA key generation failed", e);
    }
  }

  /**
   * PEM (SubjectPublicKeyInfo) of the trusted public key — the value admin-api verifies against.
   */
  public static String trustedPublicKeyPem() {
    String body = Base64.getMimeEncoder().encodeToString(TRUSTED.getPublic().getEncoded());
    return "-----BEGIN PUBLIC KEY-----\n" + body + "\n-----END PUBLIC KEY-----";
  }

  /** A valid token (trusted key, 5-minute expiry) for {@code sub} with the given {@code role}. */
  public static String validToken(String subject, String role) {
    return mint(TRUSTED, subject, role, Instant.now().plusSeconds(DEFAULT_TTL_SECONDS));
  }

  /** A token whose {@code exp} is already in the past (trusted key). */
  public static String expiredToken(String subject, String role) {
    return mint(TRUSTED, subject, role, Instant.now().minusSeconds(DEFAULT_TTL_SECONDS));
  }

  /** A token signed by the untrusted key — a valid JWS that admin-api must not accept. */
  public static String wrongKeyToken(String subject, String role) {
    return mint(OTHER, subject, role, Instant.now().plusSeconds(DEFAULT_TTL_SECONDS));
  }

  private static String mint(KeyPair keyPair, String subject, String role, Instant expiry) {
    try {
      JWTClaimsSet claims =
          new JWTClaimsSet.Builder()
              .subject(subject)
              .claim("role", role)
              .issueTime(Date.from(Instant.now().minusSeconds(10)))
              .expirationTime(Date.from(expiry))
              .build();
      SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
      jwt.sign(new RSASSASigner(keyPair.getPrivate()));
      return jwt.serialize();
    } catch (Exception e) {
      throw new IllegalStateException("JWT minting failed", e);
    }
  }
}
