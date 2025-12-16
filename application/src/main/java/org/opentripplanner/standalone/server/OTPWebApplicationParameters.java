package org.opentripplanner.standalone.server;

import java.util.List;
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
    return ClientMetricsConfig.DISABLED;
  }
}
