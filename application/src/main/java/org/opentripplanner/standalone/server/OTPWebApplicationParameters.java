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
  default ClientMetricsParameters clientMetricsParameters() {
    return new ClientMetricsParameters(
      ClientMetricsParameters.DEFAULT_CLIENT_HEADER,
      Set.of(),
      ClientMetricsParameters.DEFAULT_MONITORED_ENDPOINTS,
      ClientMetricsParameters.DEFAULT_METRIC_NAME,
      ClientMetricsParameters.DEFAULT_MIN_EXPECTED_RESPONSE_TIME,
      ClientMetricsParameters.DEFAULT_MAX_EXPECTED_RESPONSE_TIME
    );
  }
}
