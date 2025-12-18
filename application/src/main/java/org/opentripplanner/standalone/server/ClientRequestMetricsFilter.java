package org.opentripplanner.standalone.server;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Timer;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/**
 * A Jersey filter that records HTTP request response times with client identification.
 * <p>
 * The client is identified by a configurable HTTP header. Only monitored clients
 * (configured via {@code server.clientMetrics.monitoredClients}) are tracked individually;
 * unknown or missing client names are grouped under the "other" tag to prevent cardinality explosion.
 * <p>
 * The metric {@code http.client.requests} is recorded as a Timer with percentile histograms,
 * allowing analysis of response time distribution per client.
 */
public class ClientRequestMetricsFilter implements ContainerRequestFilter, ContainerResponseFilter {

  private static final String START_TIME_PROPERTY = "metrics.startTime";
  private static final String OTHER_CLIENT = "other";
  private static final String CLIENT_TAG = "client";
  private static final String URI_TAG = "uri";

  private final String clientHeader;
  private final Set<String> monitoredClients;
  private final Set<String> monitoredEndpoints;
  private final String metricName;
  private final Duration minExpectedResponseTime;
  private final Duration maxExpectedResponseTime;
  private final MeterRegistry registry;
  private final ConcurrentHashMap<TimerKey, Timer> timerCache;

  private record TimerKey(String client, String uri) {}

  /**
   * Creates a filter for recording client request metrics.
   *
   * @param clientHeader the HTTP header name used to identify the client
   * @param monitoredClients the set of client names to track individually (case-insensitive)
   * @param monitoredEndpoints the set of endpoint paths to monitor (matched by suffix)
   * @param metricName the name of the metric to record
   * @param minExpectedResponseTime minimum expected response time for histogram buckets
   * @param maxExpectedResponseTime maximum expected response time for histogram buckets
   * @param registry the meter registry to record metrics to
   */
  public ClientRequestMetricsFilter(
    String clientHeader,
    Set<String> monitoredClients,
    Set<String> monitoredEndpoints,
    String metricName,
    Duration minExpectedResponseTime,
    Duration maxExpectedResponseTime,
    MeterRegistry registry
  ) {
    this.clientHeader = clientHeader;
    this.monitoredClients = monitoredClients
      .stream()
      .map(s -> s.toLowerCase(Locale.ROOT))
      .collect(Collectors.toUnmodifiableSet());
    this.monitoredEndpoints = Set.copyOf(monitoredEndpoints);
    this.metricName = metricName;
    this.minExpectedResponseTime = Objects.requireNonNull(minExpectedResponseTime);
    this.maxExpectedResponseTime = Objects.requireNonNull(maxExpectedResponseTime);
    this.registry = registry;
    this.timerCache = new ConcurrentHashMap<>();
  }

  /**
   * Creates a filter using the global meter registry.
   *
   * @param clientHeader the HTTP header name used to identify the client
   * @param monitoredClients the set of client names to track individually
   * @param monitoredEndpoints the set of endpoint paths to monitor
   * @param metricName the name of the metric to record
   * @param minExpectedResponseTime minimum expected response time for histogram buckets
   * @param maxExpectedResponseTime maximum expected response time for histogram buckets
   */
  public ClientRequestMetricsFilter(
    String clientHeader,
    Set<String> monitoredClients,
    Set<String> monitoredEndpoints,
    String metricName,
    Duration minExpectedResponseTime,
    Duration maxExpectedResponseTime
  ) {
    this(
      clientHeader,
      monitoredClients,
      monitoredEndpoints,
      metricName,
      minExpectedResponseTime,
      maxExpectedResponseTime,
      Metrics.globalRegistry
    );
  }

  @Override
  public void filter(ContainerRequestContext requestContext) {
    if (!isMonitoredEndpoint(getRequestPath(requestContext))) {
      return;
    }
    requestContext.setProperty(START_TIME_PROPERTY, System.nanoTime());
  }

  private boolean isMonitoredEndpoint(String path) {
    return monitoredEndpoints.stream().anyMatch(path::endsWith);
  }

  private static String getRequestPath(ContainerRequestContext requestContext) {
    return requestContext.getUriInfo().getRequestUri().getPath();
  }

  @Override
  public void filter(
    ContainerRequestContext requestContext,
    ContainerResponseContext responseContext
  ) {
    Long startTime = (Long) requestContext.getProperty(START_TIME_PROPERTY);
    if (startTime == null) {
      return;
    }

    String clientName = requestContext.getHeaderString(clientHeader);
    String clientTag = resolveClientTag(clientName);
    String uri = getRequestPath(requestContext);

    long duration = System.nanoTime() - startTime;

    Timer timer = getTimer(clientTag, uri);
    timer.record(duration, TimeUnit.NANOSECONDS);
  }

  private Timer getTimer(String clientTag, String uri) {
    return timerCache.computeIfAbsent(new TimerKey(clientTag, uri), key ->
      Timer.builder(metricName)
        .description("HTTP request response time by client")
        .tag(CLIENT_TAG, key.client())
        .tag(URI_TAG, key.uri())
        .publishPercentileHistogram()
        .minimumExpectedValue(minExpectedResponseTime)
        .maximumExpectedValue(maxExpectedResponseTime)
        .register(registry)
    );
  }

  private String resolveClientTag(@Nullable String clientName) {
    if (clientName != null) {
      String lowercaseName = clientName.toLowerCase(Locale.ROOT);
      if (monitoredClients.contains(lowercaseName)) {
        return lowercaseName;
      }
    }
    return OTHER_CLIENT;
  }
}
