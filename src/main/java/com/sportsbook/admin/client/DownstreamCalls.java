package com.sportsbook.admin.client;

import com.sportsbook.admin.error.DownstreamStatusException;
import com.sportsbook.admin.error.DownstreamUnavailableException;
import java.util.function.Supplier;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Shared failure translation for the downstream clients. A non-2xx response becomes a {@link
 * DownstreamStatusException} (relayed verbatim — every service speaks RFC 7807); any transport
 * failure (connection refused, read timeout) becomes a {@link DownstreamUnavailableException} (502
 * / 504). {@link RestClientResponseException} is a subtype of {@link RestClientException}, so it is
 * caught first.
 */
final class DownstreamCalls {

  private DownstreamCalls() {}

  static <T> T execute(String service, Supplier<T> call) {
    try {
      return call.get();
    } catch (RestClientResponseException statusError) {
      throw DownstreamStatusException.from(statusError);
    } catch (RestClientException transportError) {
      throw new DownstreamUnavailableException(service, transportError);
    }
  }
}
