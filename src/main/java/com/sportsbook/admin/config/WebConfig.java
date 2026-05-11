package com.sportsbook.admin.config;

import com.sportsbook.admin.context.AdminContextArgumentResolver;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers the {@link AdminContextArgumentResolver} so controllers can take an {@code
 * AdminContext}.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

  private final AdminContextArgumentResolver adminContextArgumentResolver;

  public WebConfig(AdminContextArgumentResolver adminContextArgumentResolver) {
    this.adminContextArgumentResolver = adminContextArgumentResolver;
  }

  @Override
  public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
    resolvers.add(adminContextArgumentResolver);
  }
}
