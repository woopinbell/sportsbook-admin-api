package com.sportsbook.admin.context;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Proves the action-id generator emits real, time-ordered UUID v7 values (ADR-0003). */
class Uuid7Test {

  @Test
  void generatesVersion7WithRfcVariant() {
    UUID id = Uuid7.generate();
    assertThat(id.version()).isEqualTo(7);
    assertThat(id.variant()).isEqualTo(2); // RFC 4122 / 9562 variant (binary 10).
  }

  @Test
  void idsAreTimeOrdered() throws InterruptedException {
    UUID earlier = Uuid7.generate();
    Thread.sleep(5);
    UUID later = Uuid7.generate();
    // The 48-bit ms timestamp sits in the high bits, so a later id sorts after an earlier one.
    assertThat(
            Long.compareUnsigned(earlier.getMostSignificantBits(), later.getMostSignificantBits()))
        .isLessThan(0);
  }
}
