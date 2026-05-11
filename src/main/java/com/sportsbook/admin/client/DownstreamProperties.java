package com.sportsbook.admin.client;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Base URLs and shared timeouts for the downstream {@code /internal/v1} services admin-api
 * delegates to (ADR-0011), bound from {@code admin.downstream.*}. admin-api is not on a user
 * request path, so the timeouts are operator-friendly (fail fast enough to surface a clear 502/504,
 * loose enough not to trip on a normally slow action).
 */
@ConfigurationProperties(prefix = "admin.downstream")
public record DownstreamProperties(
    String settlementBaseUrl,
    String walletBaseUrl,
    String riskBaseUrl,
    String oddsFeedBaseUrl,
    Duration connectTimeout,
    Duration readTimeout) {

  private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofMillis(200);
  private static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(2);

  public DownstreamProperties {
    settlementBaseUrl = orDefault(settlementBaseUrl, "http://localhost:8084");
    walletBaseUrl = orDefault(walletBaseUrl, "http://localhost:8081");
    riskBaseUrl = orDefault(riskBaseUrl, "http://localhost:8083");
    oddsFeedBaseUrl = orDefault(oddsFeedBaseUrl, "http://localhost:8085");
    if (connectTimeout == null) {
      connectTimeout = DEFAULT_CONNECT_TIMEOUT;
    }
    if (readTimeout == null) {
      readTimeout = DEFAULT_READ_TIMEOUT;
    }
  }

  private static String orDefault(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }
}
