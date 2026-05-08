package com.sportsbook.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * admin-api entry point — the operator-facing REST front door (ADR-0011).
 *
 * <p>admin-api is a thin authenticated layer with no business logic of its own. It verifies
 * operator JWTs (RS256, role claim — ADMIN / TRADER / CS / READONLY), enforces an IP allowlist,
 * then delegates each operation to the owning service's {@code /internal/v1} API over HTTP,
 * propagating an {@code X-Admin-Actor} context (actor id / role / action id) plus the W3C trace
 * context. Every action is dual-recorded to a Kafka {@code admin.action} topic and a local {@code
 * audit_log} table so an operational incident can be cross-verified (ADR-0007).
 *
 * <p>{@code @ConfigurationPropertiesScan} binds the {@code admin.*} property records (downstream
 * endpoints, security, audit) without an explicit {@code @EnableConfigurationProperties}.
 */
// @SpringBootApplication is meta-annotated with @Configuration, so Spring instantiates this class
// as a bean; a private constructor would break that. Suppress the utility-class rule explicitly.
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@SpringBootApplication
@ConfigurationPropertiesScan
public class AdminApiApplication {

  public static void main(String[] args) {
    SpringApplication.run(AdminApiApplication.class, args);
  }
}
