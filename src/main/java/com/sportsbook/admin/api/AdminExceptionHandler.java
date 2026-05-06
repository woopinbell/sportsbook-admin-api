package com.sportsbook.admin.api;

import com.sportsbook.admin.error.DownstreamStatusException;
import com.sportsbook.admin.error.DownstreamUnavailableException;
import com.sportsbook.admin.error.ProblemDetailSupport;
import com.sportsbook.protocol.error.ErrorCode;
import com.sportsbook.protocol.error.ProblemDetail;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Renders admin-api errors as RFC 7807 (ADR-0004). Authentication (401) and IP (403) failures are
 * handled earlier in the security filter chain; this advice covers what happens once a request
 * reaches a controller:
 *
 * <ul>
 *   <li>A downstream non-2xx is relayed verbatim ({@link DownstreamStatusException}) — every
 *       service already speaks problem+json, so the operator sees the real reason.
 *   <li>A downstream that cannot be reached becomes 502, or 504 on a read timeout ({@link
 *       DownstreamUnavailableException}).
 *   <li>Method-security denials ({@link AccessDeniedException}, thrown by {@code @PreAuthorize}
 *       after dispatch) become 403 — same shape as the filter-level handler.
 *   <li>Bad input becomes 400; anything unexpected becomes 500.
 * </ul>
 */
@RestControllerAdvice
public class AdminExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(AdminExceptionHandler.class);

  private final ProblemDetailSupport problems;

  public AdminExceptionHandler(ProblemDetailSupport problems) {
    this.problems = problems;
  }

  /** Relay the downstream service's status + body unchanged (thin-proxy transparency). */
  @ExceptionHandler(DownstreamStatusException.class)
  public ResponseEntity<String> relayDownstream(DownstreamStatusException e) {
    MediaType contentType =
        e.contentType() != null ? e.contentType() : MediaType.APPLICATION_PROBLEM_JSON;
    return ResponseEntity.status(e.status()).contentType(contentType).body(e.body());
  }

  @ExceptionHandler(DownstreamUnavailableException.class)
  public ResponseEntity<ProblemDetail> downstreamUnavailable(
      DownstreamUnavailableException e, HttpServletRequest request) {
    HttpStatus status = e.isTimeout() ? HttpStatus.GATEWAY_TIMEOUT : HttpStatus.BAD_GATEWAY;
    URI type =
        e.isTimeout() ? ProblemDetailSupport.GATEWAY_TIMEOUT : ProblemDetailSupport.BAD_GATEWAY;
    String code = e.isTimeout() ? "GATEWAY_TIMEOUT" : "BAD_GATEWAY";
    ProblemDetail body =
        problems.build(
            status, type, status.getReasonPhrase(), code, e.getMessage(), request.getRequestURI());
    return problem(status, body);
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ProblemDetail> forbidden(
      AccessDeniedException e, HttpServletRequest request) {
    ProblemDetail body =
        problems.build(
            HttpStatus.FORBIDDEN,
            ProblemDetailSupport.FORBIDDEN,
            "Forbidden",
            "FORBIDDEN",
            "the operator role does not permit this operation",
            request.getRequestURI());
    return problem(HttpStatus.FORBIDDEN, body);
  }

  @ExceptionHandler({
    MethodArgumentNotValidException.class,
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class,
    IllegalArgumentException.class
  })
  public ResponseEntity<ProblemDetail> badRequest(Exception e, HttpServletRequest request) {
    ProblemDetail body =
        ErrorCode.VALIDATION_FAILED.toProblemDetail(
            e.getMessage(), URI.create(request.getRequestURI()), MDC.get("traceId"));
    return problem(HttpStatus.valueOf(ErrorCode.VALIDATION_FAILED.httpStatus()), body);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ProblemDetail> unexpected(Exception e, HttpServletRequest request) {
    log.error("Unhandled error on {}", request.getRequestURI(), e);
    ProblemDetail body =
        ErrorCode.INTERNAL_ERROR.toProblemDetail(
            "Internal server error", URI.create(request.getRequestURI()), MDC.get("traceId"));
    return problem(HttpStatus.INTERNAL_SERVER_ERROR, body);
  }

  private static ResponseEntity<ProblemDetail> problem(HttpStatus status, ProblemDetail body) {
    return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(body);
  }
}
