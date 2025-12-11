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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * A Jersey filter that records HTTP request response times with client identification.
 * <p>
 * The client is identified by a configurable HTTP header. Only known clients
 * (configured via {@code server.clientMetrics.knownClients}) are tracked individually;
 * unknown or missing client names are grouped under the "other" tag to prevent cardinality explosion.
 * <p>
 * The metric {@code http.client.requests} is recorded as a Timer with percentile histograms,
 * allowing analysis of response time distribution per client.
 */
public class ClientRequestMetricsFilter implements ContainerRequestFilter, ContainerResponseFilter {

  static final String METRIC_NAME = "http_server_requests";
  private static final String START_TIME_PROPERTY = "metrics.startTime";
  private static final String OTHER_CLIENT = "other";

  private static final ClientRequestMetricsFilter DISABLED = new ClientRequestMetricsFilter();

  private final String clientHeader;
  private final Set<String> knownClients;
  private final MeterRegistry registry;
  private final boolean enabled;
  private final ConcurrentHashMap<TimerKey, Timer> timerCache;

  private record TimerKey(String client, String uri) {}

  /**
   * Creates a filter for recording client request metrics.
   *
   * @param clientHeader the HTTP header name used to identify the client
   * @param knownClients the set of known client names to track individually (case-insensitive)
   * @param registry the meter registry to record metrics to
   */
  public ClientRequestMetricsFilter(
    String clientHeader,
    Set<String> knownClients,
    MeterRegistry registry
  ) {
    this.clientHeader = clientHeader;
    this.knownClients = knownClients
      .stream()
      .map(s -> s.toLowerCase(Locale.ROOT))
      .collect(Collectors.toUnmodifiableSet());
    this.registry = registry;
    this.enabled = true;
    this.timerCache = new ConcurrentHashMap<>();
  }

  /**
   * Creates a filter using the global meter registry.
   *
   * @param clientHeader the HTTP header name used to identify the client
   * @param knownClients the set of known client names to track individually
   */
  public ClientRequestMetricsFilter(String clientHeader, Set<String> knownClients) {
    this(clientHeader, knownClients, Metrics.globalRegistry);
  }

  /**
   * Private constructor for disabled filter.
   */
  private ClientRequestMetricsFilter() {
    this.clientHeader = null;
    this.knownClients = Set.of();
    this.registry = null;
    this.enabled = false;
    this.timerCache = null;
  }

  /**
   * Returns a disabled filter that does nothing.
   */
  public static ClientRequestMetricsFilter disabled() {
    return DISABLED;
  }

  @Override
  public void filter(ContainerRequestContext requestContext) {
    if (enabled) {
      requestContext.setProperty(START_TIME_PROPERTY, System.nanoTime());
    }
  }

  @Override
  public void filter(
    ContainerRequestContext requestContext,
    ContainerResponseContext responseContext
  ) {
    if (!enabled) {
      return;
    }

    Long startTime = (Long) requestContext.getProperty(START_TIME_PROPERTY);
    if (startTime == null) {
      return;
    }

    String clientName = requestContext.getHeaderString(clientHeader);
    String clientTag = resolveClientTag(clientName);
    String uri = requestContext.getUriInfo().getRequestUri().getPath();

    long duration = System.nanoTime() - startTime;

    Timer timer = getTimer(clientTag, uri);
    timer.record(duration, TimeUnit.NANOSECONDS);
  }

  private Timer getTimer(String clientTag, String uri) {
    return timerCache.computeIfAbsent(new TimerKey(clientTag, uri), key ->
      Timer.builder(METRIC_NAME)
        .description("HTTP request response time by client")
        .tag("client", key.client())
        .tag("uri", key.uri())
        .publishPercentileHistogram()
        .minimumExpectedValue(Duration.ofMillis(100))
        .maximumExpectedValue(Duration.ofMillis(1000))
        .register(registry)
    );
  }

  private String resolveClientTag(String clientName) {
    if (clientName != null) {
      String lowercaseName = clientName.toLowerCase(Locale.ROOT);
      if (knownClients.contains(lowercaseName)) {
        return lowercaseName;
      }
    }
    return OTHER_CLIENT;
  }
}
