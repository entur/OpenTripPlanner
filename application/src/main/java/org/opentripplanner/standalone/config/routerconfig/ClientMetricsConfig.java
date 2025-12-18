package org.opentripplanner.standalone.config.routerconfig;

import static org.opentripplanner.standalone.config.framework.json.OtpVersion.V2_7;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.opentripplanner.standalone.config.framework.json.NodeAdapter;

/**
 * Configuration for HTTP client request metrics.
 *
 * @param clientHeader the HTTP header name used to identify the client
 * @param monitoredClients the set of client names to track individually
 * @param monitoredEndpoints the set of endpoint paths to monitor for metrics
 * @param metricName the name of the metric to record
 * @param minExpectedResponseTime minimum expected response time for histogram buckets
 * @param maxExpectedResponseTime maximum expected response time for histogram buckets
 */
public record ClientMetricsConfig(
  String clientHeader,
  Set<String> monitoredClients,
  Set<String> monitoredEndpoints,
  String metricName,
  Duration minExpectedResponseTime,
  Duration maxExpectedResponseTime
) {
  public static final String DEFAULT_CLIENT_HEADER = "x-client-name";
  public static final Set<String> DEFAULT_MONITORED_ENDPOINTS = Set.of(
    "/transmodel/v3",
    "/gtfs/v1/"
  );
  public static final String DEFAULT_METRIC_NAME = "otp_http_server_requests";
  public static final Duration DEFAULT_MIN_EXPECTED_RESPONSE_TIME = Duration.ofMillis(10);
  public static final Duration DEFAULT_MAX_EXPECTED_RESPONSE_TIME = Duration.ofMillis(10000);

  public ClientMetricsConfig {
    monitoredClients = Set.copyOf(monitoredClients);
    monitoredEndpoints = Set.copyOf(monitoredEndpoints);
  }

  public static ClientMetricsConfig mapClientMetrics(String parameterName, NodeAdapter root) {
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
    return new ClientMetricsConfig(
      c
        .of("clientHeader")
        .since(V2_7)
        .summary("HTTP header name used to identify the client.")
        .asString(DEFAULT_CLIENT_HEADER),
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
          .asStringList(DEFAULT_MONITORED_ENDPOINTS.stream().toList())
      ),
      c
        .of("metricName")
        .since(V2_7)
        .summary("Name of the metric to record.")
        .asString(DEFAULT_METRIC_NAME),
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
        .asDuration(DEFAULT_MIN_EXPECTED_RESPONSE_TIME),
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
        .asDuration(DEFAULT_MAX_EXPECTED_RESPONSE_TIME)
    );
  }
}
