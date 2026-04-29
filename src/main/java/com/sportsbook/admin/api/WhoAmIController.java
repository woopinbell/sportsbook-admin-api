package com.sportsbook.admin.api;

import com.sportsbook.admin.context.AdminActor;
import com.sportsbook.admin.security.AdminSecurityProperties;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Token introspection for operators: returns the actor id and role resolved from the verified JWT.
 * Reachable by any authenticated operator (no role guard) — it is how an operator confirms the
 * token and role admin-api sees before attempting a guarded action. A request with no/invalid token
 * is stopped at the security layer (401) and never reaches this method.
 */
@RestController
public class WhoAmIController {

  private final String roleClaim;

  public WhoAmIController(AdminSecurityProperties properties) {
    this.roleClaim = properties.jwt().roleClaim();
  }

  @GetMapping("/admin/v1/whoami")
  public AdminActor whoami(@AuthenticationPrincipal Jwt jwt) {
    return AdminActor.from(jwt, roleClaim);
  }
}
