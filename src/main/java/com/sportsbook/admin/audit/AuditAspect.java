package com.sportsbook.admin.audit;

import com.sportsbook.admin.context.AdminContext;
import com.sportsbook.admin.error.DownstreamStatusException;
import com.sportsbook.admin.error.DownstreamUnavailableException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.Ordered;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.annotation.Order;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * Records every {@link Audited} action to the audit trail (ADR-0011). Ordered {@link
 * Ordered#HIGHEST_PRECEDENCE} so it wraps the {@code @PreAuthorize} method-security advice too —
 * meaning an authorization denial is itself audited (a security-relevant failed attempt), not just
 * the actions that pass. The downstream outcome determines the recorded HTTP status and SUCCESS /
 * FAILED; the action's {@code target} / {@code reason} come from SpEL over the method arguments.
 */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AuditAspect {

  private static final Logger log = LoggerFactory.getLogger(AuditAspect.class);
  private static final SpelExpressionParser PARSER = new SpelExpressionParser();
  private static final ParameterNameDiscoverer PARAM_NAMES = new DefaultParameterNameDiscoverer();
  private static final int MIN_REDIRECT_STATUS = HttpStatus.MULTIPLE_CHOICES.value();

  private final AuditService auditService;

  public AuditAspect(AuditService auditService) {
    this.auditService = auditService;
  }

  @Around("@annotation(com.sportsbook.admin.audit.Audited)")
  public Object audit(ProceedingJoinPoint joinPoint) throws Throwable {
    Audited audited =
        ((MethodSignature) joinPoint.getSignature()).getMethod().getAnnotation(Audited.class);
    AdminContext context = findContext(joinPoint.getArgs());
    String target = evaluate(audited.target(), joinPoint);
    String reason = evaluate(audited.reason(), joinPoint);
    try {
      Object result = joinPoint.proceed();
      record(context, audited.value(), target, reason, statusOf(result));
      return result;
    } catch (Throwable failure) {
      record(context, audited.value(), target, reason, statusOf(failure));
      throw failure;
    }
  }

  private void record(
      AdminContext context, AdminAction action, String target, String reason, int httpStatus) {
    if (context == null) {
      log.warn("audited method {} had no AdminContext argument; skipping audit", action);
      return;
    }
    String outcome =
        httpStatus >= HttpStatus.OK.value() && httpStatus < MIN_REDIRECT_STATUS
            ? "SUCCESS"
            : "FAILED";
    auditService.record(context, action, target, reason, outcome, httpStatus);
  }

  private static AdminContext findContext(Object[] args) {
    for (Object arg : args) {
      if (arg instanceof AdminContext context) {
        return context;
      }
    }
    return null;
  }

  private static int statusOf(Object result) {
    if (result instanceof ResponseEntity<?> response) {
      return response.getStatusCode().value();
    }
    return HttpStatus.OK.value();
  }

  private static int statusOf(Throwable failure) {
    if (failure instanceof DownstreamStatusException e) {
      return e.status().value();
    }
    if (failure instanceof DownstreamUnavailableException e) {
      return e.isTimeout() ? HttpStatus.GATEWAY_TIMEOUT.value() : HttpStatus.BAD_GATEWAY.value();
    }
    if (failure instanceof AccessDeniedException) {
      return HttpStatus.FORBIDDEN.value();
    }
    return HttpStatus.INTERNAL_SERVER_ERROR.value();
  }

  private String evaluate(String expression, ProceedingJoinPoint joinPoint) {
    if (expression == null || expression.isBlank()) {
      return null;
    }
    MethodSignature signature = (MethodSignature) joinPoint.getSignature();
    EvaluationContext context =
        new MethodBasedEvaluationContext(
            joinPoint.getTarget(), signature.getMethod(), joinPoint.getArgs(), PARAM_NAMES);
    Object value = PARSER.parseExpression(expression).getValue(context);
    return value == null ? null : value.toString();
  }
}
