package com.sportsbook.admin.error;

import java.net.SocketTimeoutException;

/**
 * A downstream {@code /internal/v1} service could not be reached at all — connection refused, DNS
 * failure, or a read timeout. Distinct from {@link DownstreamStatusException} (which carries a real
 * HTTP status): here there is no answer to relay, so admin-api returns 502 Bad Gateway, or 504
 * Gateway Timeout when the cause was a read timeout.
 */
public class DownstreamUnavailableException extends RuntimeException {

  private final String service;
  private final boolean timeout;

  public DownstreamUnavailableException(String service, Throwable cause) {
    super(service + " is unavailable: " + cause.getMessage(), cause);
    this.service = service;
    this.timeout = isTimeout(cause);
  }

  public String service() {
    return service;
  }

  public boolean isTimeout() {
    return timeout;
  }

  private static boolean isTimeout(Throwable cause) {
    for (Throwable t = cause; t != null; t = t.getCause()) {
      if (t instanceof SocketTimeoutException) {
        return true;
      }
    }
    return false;
  }
}
