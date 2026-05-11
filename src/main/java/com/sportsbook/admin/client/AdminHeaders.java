package com.sportsbook.admin.client;

import com.sportsbook.admin.context.AdminContext;
import org.springframework.http.HttpHeaders;

/**
 * The admin context headers admin-api stamps on every downstream {@code /internal/v1} call
 * (ADR-0011). Each owning service writes these into its own audit log, so an operational action is
 * traceable end to end — who ({@code actorId} / {@code actorRole}), which action ({@code
 * actionId}), and which distributed trace ({@code traceId}).
 */
public final class AdminHeaders {

  public static final String ACTOR_ID = "X-Admin-Actor-Id";
  public static final String ACTOR_ROLE = "X-Admin-Actor-Role";
  public static final String ACTION_ID = "X-Admin-Action-Id";
  public static final String TRACE_ID = "X-Trace-Id";

  private AdminHeaders() {}

  /** Applies the admin context to an outgoing request's headers. */
  public static void apply(HttpHeaders headers, AdminContext context) {
    headers.set(ACTOR_ID, context.actor().id());
    headers.set(ACTOR_ROLE, context.actor().roleName());
    headers.set(ACTION_ID, context.actionId().toString());
    if (context.traceId() != null) {
      headers.set(TRACE_ID, context.traceId());
    }
  }
}
