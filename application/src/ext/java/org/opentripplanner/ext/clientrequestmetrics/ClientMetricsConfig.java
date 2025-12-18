package org.opentripplanner.ext.clientrequestmetrics;

import static org.opentripplanner.standalone.config.framework.json.OtpVersion.V2_7;

import java.util.List;
import java.util.Set;
import org.opentripplanner.standalone.config.framework.json.NodeAdapter;

/**
 * This class is responsible for mapping client metrics JSON configuration into parameters.
 */
public class ClientMetricsConfig {

  private ClientMetricsConfig() {}

  public static ClientMetricsParameters mapClientMetrics(String parameterName, NodeAdapter root) {
    var c = root
      .of(parameterName)
      .since(V2_7)
      .summary("Configuration for HTTP client request metrics.")
      .description(
        """
        When enabled, records response time metrics per client. The client is identified by a
        configurable HTTP header (`clientHeader`). Only clients in the `monitoredClients` list are
        tracked individually; unknown clients are grouped under "other" to prevent metric
        cardinality explosion. Requires the ActuatorAPI feature to be enabled.
        """
      )
      .asObject();
    return new ClientMetricsParameters(
      c
        .of("clientHeader")
        .since(V2_7)
        .summary("HTTP header name used to identify the client.")
        .asString(ClientMetricsParameters.DEFAULT_CLIENT_HEADER),
      Set.copyOf(
        c
          .of("monitoredClients")
          .since(V2_7)
          .summary("List of client names to track individually.")
          .description(
            """
            Clients not in this list will be grouped under "other". This prevents high cardinality
            metrics when unknown clients send requests.
            """
          )
          .asStringList(List.of())
      ),
      Set.copyOf(
        c
          .of("monitoredEndpoints")
          .since(V2_7)
          .summary("List of endpoint paths to monitor for metrics.")
          .description(
            """
            Only requests to these endpoints will be tracked. Endpoint paths are matched using
            suffix matching (request path must end with one of these values).
            """
          )
          .asStringList(ClientMetricsParameters.DEFAULT_MONITORED_ENDPOINTS.stream().toList())
      ),
      c
        .of("metricName")
        .since(V2_7)
        .summary("Name of the metric to record.")
        .asString(ClientMetricsParameters.DEFAULT_METRIC_NAME),
      c
        .of("minExpectedResponseTime")
        .since(V2_7)
        .summary("Minimum expected response time for histogram buckets.")
        .description(
          """
          Use duration format with units: `s` (seconds), `m` (minutes), `h` (hours).
          For milliseconds, use fractional seconds (e.g., `0.01s` for 10ms, `0.05s` for 50ms).
          """
        )
        .asDuration(ClientMetricsParameters.DEFAULT_MIN_EXPECTED_RESPONSE_TIME),
      c
        .of("maxExpectedResponseTime")
        .since(V2_7)
        .summary("Maximum expected response time for histogram buckets.")
        .description(
          """
          Use duration format with units: `s` (seconds), `m` (minutes), `h` (hours).
          For milliseconds, use fractional seconds (e.g., `0.01s` for 10ms, `0.05s` for 50ms).
          """
        )
        .asDuration(ClientMetricsParameters.DEFAULT_MAX_EXPECTED_RESPONSE_TIME)
    );
  }
}
