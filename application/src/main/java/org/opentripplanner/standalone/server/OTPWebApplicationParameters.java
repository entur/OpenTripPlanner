package org.opentripplanner.standalone.server;

import java.util.List;
import java.util.Set;
import org.opentripplanner.ext.clientrequestmetrics.ClientMetricsParameters;

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
  ClientMetricsParameters clientMetricsParameters();
}
