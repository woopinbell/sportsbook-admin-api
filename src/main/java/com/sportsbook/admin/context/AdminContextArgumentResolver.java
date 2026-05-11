package com.sportsbook.admin.context;

import com.sportsbook.admin.security.AdminSecurityProperties;
import org.slf4j.MDC;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Injects an {@link AdminContext} into any controller method that declares one. Resolves the {@link
 * AdminActor} from the verified JWT in the security context, mints a fresh {@link Uuid7} action id,
 * and captures the current trace id from the logging MDC. Reaching a controller method already
 * implies authentication (the security chain rejects anonymous requests at 401), so the principal
 * is always a {@link Jwt} here.
 */
@Component
public class AdminContextArgumentResolver implements HandlerMethodArgumentResolver {

  private final String roleClaim;

  public AdminContextArgumentResolver(AdminSecurityProperties properties) {
    this.roleClaim = properties.jwt().roleClaim();
  }

  @Override
  public boolean supportsParameter(MethodParameter parameter) {
    return AdminContext.class.equals(parameter.getParameterType());
  }

  @Override
  public Object resolveArgument(
      MethodParameter parameter,
      ModelAndViewContainer mavContainer,
      NativeWebRequest webRequest,
      WebDataBinderFactory binderFactory) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    Jwt jwt = (Jwt) authentication.getPrincipal();
    AdminActor actor = AdminActor.from(jwt, roleClaim);
    return new AdminContext(actor, Uuid7.generate(), MDC.get("traceId"));
  }
}
