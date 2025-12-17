package org.opentripplanner.standalone.config.routerconfig;

import java.util.Set;

/**
 * Configuration for HTTP client request metrics.
 *
 * @param enabled whether client metrics are enabled
 * @param clientHeader the HTTP header name used to identify the client
 * @param knownClients the set of known client names to track individually
 */
public record ClientMetricsConfig(boolean enabled, String clientHeader, Set<String> knownClients) {
  public static final String DEFAULT_CLIENT_HEADER = "x-client-name";
  public static final ClientMetricsConfig DISABLED = new ClientMetricsConfig(
    false,
    DEFAULT_CLIENT_HEADER,
    Set.of()
  );

  public ClientMetricsConfig {
    knownClients = Set.copyOf(knownClients);
  }
}
