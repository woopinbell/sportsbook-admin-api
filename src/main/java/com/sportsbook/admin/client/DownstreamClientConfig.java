package com.sportsbook.admin.client;

import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * One {@link RestClient} per downstream service, each pinned to its base URL with the shared
 * connect / read timeouts (ADR-0011 delegation). The autoconfigured builder is cloned so Boot's
 * customizations (Micrometer observation — which also propagates the W3C trace context downstream)
 * carry over. Per-request admin headers are added at the call site via {@link AdminHeaders}.
 */
@Configuration
public class DownstreamClientConfig {

  @Bean
  RestClient settlementRestClient(RestClient.Builder builder, DownstreamProperties properties) {
    return build(builder, properties.settlementBaseUrl(), properties);
  }

  @Bean
  RestClient walletRestClient(RestClient.Builder builder, DownstreamProperties properties) {
    return build(builder, properties.walletBaseUrl(), properties);
  }

  @Bean
  RestClient riskRestClient(RestClient.Builder builder, DownstreamProperties properties) {
    return build(builder, properties.riskBaseUrl(), properties);
  }

  @Bean
  RestClient oddsFeedRestClient(RestClient.Builder builder, DownstreamProperties properties) {
    return build(builder, properties.oddsFeedBaseUrl(), properties);
  }

  private static RestClient build(
      RestClient.Builder builder, String baseUrl, DownstreamProperties properties) {
    return builder.clone().baseUrl(baseUrl).requestFactory(requestFactory(properties)).build();
  }

  private static ClientHttpRequestFactory requestFactory(DownstreamProperties properties) {
    ClientHttpRequestFactorySettings settings =
        ClientHttpRequestFactorySettings.DEFAULTS
            .withConnectTimeout(properties.connectTimeout())
            .withReadTimeout(properties.readTimeout());
    return ClientHttpRequestFactories.get(settings);
  }
}
