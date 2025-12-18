package org.opentripplanner.standalone.server;

import java.util.List;
import java.util.Set;
import org.opentripplanner.standalone.config.routerconfig.ClientMetricsConfig;

/**
 * Parameters used to configure the {@link OTPWebApplication}.
 */
public interface OTPWebApplicationParameters {
  /**
   * The HTTP request/response trace/correlation-id headers to use.
   */
  List<RequestTraceParameter> traceParameters();

  default boolean requestTraceLoggingEnabled() {
    return traceParameters().stream().anyMatch(RequestTraceParameter::hasLogKey);
  }

  /**
   * Configuration for client request metrics.
   */
  default ClientMetricsConfig clientMetrics() {
    return new ClientMetricsConfig(
      ClientMetricsConfig.DEFAULT_CLIENT_HEADER,
      Set.of(),
      ClientMetricsConfig.DEFAULT_MONITORED_ENDPOINTS,
      ClientMetricsConfig.DEFAULT_METRIC_NAME,
      ClientMetricsConfig.DEFAULT_MIN_EXPECTED_RESPONSE_TIME,
      ClientMetricsConfig.DEFAULT_MAX_EXPECTED_RESPONSE_TIME
    );
  }
}
