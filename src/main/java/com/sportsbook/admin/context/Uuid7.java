package com.sportsbook.admin.context;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * Generates UUID v7 (RFC 9562) — a 48-bit Unix-millis timestamp prefix plus random bits, so ids are
 * time-ordered (ADR-0003). The business services get this ordering for free from Hibernate's
 * {@code @UuidGenerator(style = TIME)} at persist time, but admin-api needs an id <em>before</em>
 * any persistence: the action id is minted up front and used three ways — the {@code
 * X-Admin-Action-Id} header sent downstream, the wallet refund {@code Idempotency-Key}, and the
 * {@code audit_log} primary key — so it is generated here.
 */
public final class Uuid7 {

  private static final SecureRandom RANDOM = new SecureRandom();

  // Bit layout of the most-significant long: [ 48-bit ms timestamp | 4-bit version | 12-bit rand ].
  private static final int TIMESTAMP_SHIFT = 16;
  private static final long TIMESTAMP_MASK = 0xFFFFFFFFFFFFL;
  private static final long VERSION_7_BITS = 0x7000L; // 0x7 << 12, the version nibble.
  private static final int RAND_A_BOUND = 0x1000; // 12 bits.

  // Least-significant long: [ 2-bit variant '10' | 62-bit rand ].
  private static final long VARIANT_BITS = 0x8000000000000000L; // 0b10 << 62.
  private static final long RAND_B_MASK = 0x3FFFFFFFFFFFFFFFL; // low 62 bits.

  private Uuid7() {}

  public static UUID generate() {
    long timestamp = System.currentTimeMillis() & TIMESTAMP_MASK;
    long randA = RANDOM.nextInt(RAND_A_BOUND);
    long mostSignificant = (timestamp << TIMESTAMP_SHIFT) | VERSION_7_BITS | randA;
    long leastSignificant = VARIANT_BITS | (RANDOM.nextLong() & RAND_B_MASK);
    return new UUID(mostSignificant, leastSignificant);
  }
}
