package com.sportsbook.admin.security;

import com.sportsbook.admin.error.ProblemDetailSupport;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rejects {@code /admin/**} requests whose client IP is not in the configured CIDR allowlist
 * (ADR-0011 — corp network / VPN only in production). Runs before the bearer-token filter so a
 * disallowed source never reaches authentication. Health / readiness probes and the Prometheus
 * scrape are on {@code /actuator/**} and are intentionally not guarded (k8s probes originate from
 * the node).
 *
 * <p>Client IP precedence: the left-most {@code X-Forwarded-For} hop if present, else the socket
 * remote address. NOTE: {@code X-Forwarded-For} is spoofable unless the only path to admin-api is
 * through a trusted ingress that overwrites it — which is exactly the V1 deployment assumption
 * (external traffic blocked by k8s NetworkPolicy, ADR-0011). Outside that boundary this header must
 * not be trusted.
 */
public class IpAllowlistFilter extends OncePerRequestFilter {

  private static final String ADMIN_PATH_PREFIX = "/admin";

  private final List<IpAddressMatcher> allowlist;
  private final ProblemDetailSupport problems;

  public IpAllowlistFilter(List<String> cidrs, ProblemDetailSupport problems) {
    this.allowlist = cidrs.stream().map(IpAddressMatcher::new).toList();
    this.problems = problems;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getRequestURI().startsWith(ADMIN_PATH_PREFIX);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String clientIp = clientIp(request);
    boolean allowed = allowlist.stream().anyMatch(matcher -> matches(matcher, clientIp));
    if (!allowed) {
      problems.write(
          response,
          HttpStatus.FORBIDDEN,
          ProblemDetailSupport.IP_NOT_ALLOWED,
          "IP not allowed",
          "IP_NOT_ALLOWED",
          "client address is not in the admin allowlist",
          request);
      return;
    }
    chain.doFilter(request, response);
  }

  private static boolean matches(IpAddressMatcher matcher, String ip) {
    try {
      return matcher.matches(ip);
    } catch (IllegalArgumentException notAnIp) {
      // A non-IP remote address (e.g. a hostname) can never match a CIDR.
      return false;
    }
  }

  private static String clientIp(HttpServletRequest request) {
    String forwarded = request.getHeader("X-Forwarded-For");
    if (forwarded != null && !forwarded.isBlank()) {
      return forwarded.split(",")[0].trim();
    }
    return request.getRemoteAddr();
  }
}
