package com.sportsbook.admin.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller method as an audited operator action (ADR-0011). The {@link AuditAspect}
 * records every invocation — success or failure, including an authorization denial — to the audit
 * trail.
 *
 * <p>{@code target} (and optional {@code reason}) are SpEL expressions over the method arguments,
 * e.g. {@code target = "#betId"} or {@code target = "#eventId + '/' + #marketId"}, {@code reason =
 * "#request.reason()"}.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {

  AdminAction value();

  String target();

  String reason() default "";
}
