package com.sportsbook.admin.error;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClientResponseException;

/**
 * A downstream {@code /internal/v1} service answered with a non-2xx status (e.g. 404 bet not found,
 * 409 not-PENDING, 400 validation). admin-api is a thin proxy, so it relays the downstream status
 * and body verbatim — every service already speaks RFC 7807 (ADR-0004), so the operator sees the
 * real reason rather than a flattened generic error.
 */
public class DownstreamStatusException extends RuntimeException {

  private final transient HttpStatusCode status;
  private final transient MediaType contentType;
  private final String body;

  public DownstreamStatusException(HttpStatusCode status, MediaType contentType, String body) {
    super("downstream responded " + status);
    this.status = status;
    this.contentType = contentType;
    this.body = body;
  }

  /** Builds from the exception RestClient throws on a 4xx/5xx response. */
  public static DownstreamStatusException from(RestClientResponseException e) {
    return new DownstreamStatusException(
        e.getStatusCode(),
        e.getResponseHeaders() == null ? null : e.getResponseHeaders().getContentType(),
        e.getResponseBodyAsString());
  }

  public HttpStatusCode status() {
    return status;
  }

  public MediaType contentType() {
    return contentType;
  }

  public String body() {
    return body;
  }
}
