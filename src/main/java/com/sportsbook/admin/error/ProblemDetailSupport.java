package com.sportsbook.admin.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportsbook.protocol.error.ProblemDetail;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

/**
 * Builds and writes RFC 7807 {@code application/problem+json} bodies (ADR-0004) using the
 * shared-protocol {@link ProblemDetail} shape. Used by the security layer — the {@code
 * AuthenticationEntryPoint} (401), {@code AccessDeniedHandler} (403) and the IP allowlist filter —
 * which run outside {@code @RestControllerAdvice} and must serialize the body to the raw response
 * themselves. The controller exception handler (ADR-0004) returns {@code ResponseEntity} instead.
 *
 * <p>Generic auth codes are intentionally not in the shared {@link
 * com.sportsbook.protocol.error.ErrorCode} catalog (it seeds only betting rejection codes), so the
 * type URIs and codes are defined here.
 */
@Component
public class ProblemDetailSupport {

  public static final URI UNAUTHORIZED = URI.create("https://sportsbook/errors/unauthorized");
  public static final URI FORBIDDEN = URI.create("https://sportsbook/errors/forbidden");
  public static final URI IP_NOT_ALLOWED = URI.create("https://sportsbook/errors/ip-not-allowed");
  public static final URI BAD_GATEWAY = URI.create("https://sportsbook/errors/bad-gateway");
  public static final URI GATEWAY_TIMEOUT = URI.create("https://sportsbook/errors/gateway-timeout");

  private final ObjectMapper mapper;

  public ProblemDetailSupport(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  /** Builds a ProblemDetail; {@code correlationId} is the current trace id from the logging MDC. */
  public ProblemDetail build(
      HttpStatus status, URI type, String title, String code, String detail, String instance) {
    return new ProblemDetail(
        type,
        title,
        status.value(),
        code,
        detail,
        instance == null ? null : URI.create(instance),
        MDC.get("traceId"));
  }

  /**
   * Serializes a problem body straight to the servlet response (for filters / security handlers).
   */
  public void write(
      HttpServletResponse response,
      HttpStatus status,
      URI type,
      String title,
      String code,
      String detail,
      HttpServletRequest request)
      throws IOException {
    ProblemDetail body = build(status, type, title, code, detail, request.getRequestURI());
    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    mapper.writeValue(response.getOutputStream(), body);
  }
}
