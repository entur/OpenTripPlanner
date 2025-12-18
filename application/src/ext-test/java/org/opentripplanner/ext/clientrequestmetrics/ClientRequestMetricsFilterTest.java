package org.opentripplanner.ext.clientrequestmetrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.core.UriInfo;
import java.net.URI;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ClientRequestMetricsFilterTest {

  private static final String CLIENT_HEADER = "et-client-name";
  private static final String MONITORED_ENDPOINT = "/transmodel/v3";
  private static final String GTFS_ENDPOINT = "/gtfs/v1/";
  private static final Set<String> MONITORED_ENDPOINTS = Set.of(MONITORED_ENDPOINT, GTFS_ENDPOINT);
  private static final String METRIC_NAME = "otp_http_server_requests";
  private static final Duration MIN_EXPECTED_RESPONSE_TIME = Duration.ofMillis(10);
  private static final Duration MAX_EXPECTED_RESPONSE_TIME = Duration.ofMillis(10000);
  private SimpleMeterRegistry registry;
  private ClientRequestMetricsFilter filter;

  @BeforeEach
  void setUp() {
    registry = new SimpleMeterRegistry();
    filter = new ClientRequestMetricsFilter(
      CLIENT_HEADER,
      Set.of("app1", "app2", "web-client"),
      MONITORED_ENDPOINTS,
      METRIC_NAME,
      MIN_EXPECTED_RESPONSE_TIME,
      MAX_EXPECTED_RESPONSE_TIME,
      registry
    );
  }

  @Test
  void timersArePreCreatedAtStartup() {
    // Verify timers exist for all client/endpoint combinations before any requests
    assertNotNull(
      registry.find(METRIC_NAME).tag("client", "app1").tag("uri", MONITORED_ENDPOINT).timer()
    );
    assertNotNull(
      registry.find(METRIC_NAME).tag("client", "app1").tag("uri", GTFS_ENDPOINT).timer()
    );
    assertNotNull(
      registry.find(METRIC_NAME).tag("client", "app2").tag("uri", MONITORED_ENDPOINT).timer()
    );
    assertNotNull(
      registry.find(METRIC_NAME).tag("client", "app2").tag("uri", GTFS_ENDPOINT).timer()
    );
    assertNotNull(
      registry.find(METRIC_NAME).tag("client", "web-client").tag("uri", MONITORED_ENDPOINT).timer()
    );
    assertNotNull(
      registry.find(METRIC_NAME).tag("client", "web-client").tag("uri", GTFS_ENDPOINT).timer()
    );
    assertNotNull(
      registry.find(METRIC_NAME).tag("client", "other").tag("uri", MONITORED_ENDPOINT).timer()
    );
    assertNotNull(
      registry.find(METRIC_NAME).tag("client", "other").tag("uri", GTFS_ENDPOINT).timer()
    );

    // All timers should have 0 count initially
    assertEquals(
      0,
      registry
        .find(METRIC_NAME)
        .tag("client", "app1")
        .tag("uri", MONITORED_ENDPOINT)
        .timer()
        .count()
    );
  }

  @Test
  void recordsMetricForKnownClient() throws Exception {
    recordRequest("app1", MONITORED_ENDPOINT);

    Timer timer = registry
      .find(METRIC_NAME)
      .tag("client", "app1")
      .tag("uri", MONITORED_ENDPOINT)
      .timer();
    assertNotNull(timer, "Timer should exist for known client");
    assertEquals(1, timer.count());
  }

  @Test
  void matchesClientNameCaseInsensitively() {
    // Send request with uppercase client name "APP1" which should match configured "app1"
    recordRequest("APP1", MONITORED_ENDPOINT);
    // Send request with mixed case "App1"
    recordRequest("App1", MONITORED_ENDPOINT);
    // Send request with lowercase "app1"
    recordRequest("app1", MONITORED_ENDPOINT);

    // All three should be recorded under the lowercase "app1" tag
    Timer timer = registry
      .find(METRIC_NAME)
      .tag("client", "app1")
      .tag("uri", MONITORED_ENDPOINT)
      .timer();
    assertNotNull(timer, "Timer should exist for case-insensitive matched client");
    assertEquals(3, timer.count());
  }

  @Test
  void recordsMetricForUnknownClientAsOther() {
    recordRequest("unknown-app", MONITORED_ENDPOINT);

    Timer timer = registry
      .find(METRIC_NAME)
      .tag("client", "other")
      .tag("uri", MONITORED_ENDPOINT)
      .timer();
    assertNotNull(timer, "Timer should exist with 'other' tag for unknown client");
    assertEquals(1, timer.count());
  }

  @Test
  void recordsMetricForMissingHeaderAsOther() {
    recordRequest(null, MONITORED_ENDPOINT);

    Timer timer = registry
      .find(METRIC_NAME)
      .tag("client", "other")
      .tag("uri", MONITORED_ENDPOINT)
      .timer();
    assertNotNull(timer, "Timer should exist with 'other' tag for missing header");
    assertEquals(1, timer.count());
  }

  @Test
  void handlesMultipleClients() {
    // First request from app1
    recordRequest("app1", MONITORED_ENDPOINT);
    // Second request from app2
    recordRequest("app2", MONITORED_ENDPOINT);
    // Third request from unknown
    recordRequest("unknown", MONITORED_ENDPOINT);
    // Fourth request from app1 again
    recordRequest("app1", MONITORED_ENDPOINT);

    assertEquals(
      2,
      registry
        .find(METRIC_NAME)
        .tag("client", "app1")
        .tag("uri", MONITORED_ENDPOINT)
        .timer()
        .count()
    );
    assertEquals(
      1,
      registry
        .find(METRIC_NAME)
        .tag("client", "app2")
        .tag("uri", MONITORED_ENDPOINT)
        .timer()
        .count()
    );
    assertEquals(
      1,
      registry
        .find(METRIC_NAME)
        .tag("client", "other")
        .tag("uri", MONITORED_ENDPOINT)
        .timer()
        .count()
    );
  }

  @Test
  void reusesTimerForSameClientAndEndpoint() {
    // Record multiple requests from the same client to the same endpoint
    recordRequest("app1", MONITORED_ENDPOINT);
    recordRequest("app1", MONITORED_ENDPOINT);
    recordRequest("app1", MONITORED_ENDPOINT);

    // All requests should use the same timer instance
    Timer timer = registry
      .find(METRIC_NAME)
      .tag("client", "app1")
      .tag("uri", MONITORED_ENDPOINT)
      .timer();
    assertNotNull(timer);
    assertEquals(3, timer.count());

    // Only one timer should exist for this client/endpoint combination
    var timers = registry
      .find(METRIC_NAME)
      .tag("client", "app1")
      .tag("uri", MONITORED_ENDPOINT)
      .timers();
    assertEquals(1, timers.size());
  }

  @Test
  void doesNotRecordMetricsForNonMonitoredEndpoint() {
    var requestContext = mock(ContainerRequestContext.class);
    var responseContext = mock(ContainerResponseContext.class);
    var uriInfo = mock(UriInfo.class);

    when(uriInfo.getRequestUri()).thenReturn(URI.create("/some/other/endpoint"));
    when(requestContext.getUriInfo()).thenReturn(uriInfo);
    when(requestContext.getProperty("metrics.startTime")).thenReturn(null);

    filter.filter(requestContext);
    filter.filter(requestContext, responseContext);

    // All pre-created timers should still have 0 count
    assertEquals(
      0,
      registry
        .find(METRIC_NAME)
        .tag("client", "app1")
        .tag("uri", MONITORED_ENDPOINT)
        .timer()
        .count()
    );
    assertEquals(
      0,
      registry
        .find(METRIC_NAME)
        .tag("client", "other")
        .tag("uri", MONITORED_ENDPOINT)
        .timer()
        .count()
    );
  }

  @Test
  void recordsMetricsForMultipleMonitoredEndpoints() {
    // Request to first endpoint
    recordRequest("app1", MONITORED_ENDPOINT);
    // Request to second endpoint
    recordRequest("app1", GTFS_ENDPOINT);

    Timer transmodelTimer = registry
      .find(METRIC_NAME)
      .tag("client", "app1")
      .tag("uri", MONITORED_ENDPOINT)
      .timer();
    assertNotNull(transmodelTimer);
    assertEquals(1, transmodelTimer.count());

    Timer gtfsTimer = registry
      .find(METRIC_NAME)
      .tag("client", "app1")
      .tag("uri", GTFS_ENDPOINT)
      .timer();
    assertNotNull(gtfsTimer);
    assertEquals(1, gtfsTimer.count());
  }

  @Test
  void uriTagUsesMonitoredEndpointNotRequestPath() {
    // Request with a path that ends with the monitored endpoint but has extra prefix
    var requestContext = mock(ContainerRequestContext.class);
    var responseContext = mock(ContainerResponseContext.class);
    var uriInfo = mock(UriInfo.class);

    // Path is "/otp/routers/default/transmodel/v3" but should be tagged as "/transmodel/v3"
    when(uriInfo.getRequestUri()).thenReturn(
      URI.create("/otp/routers/default" + MONITORED_ENDPOINT)
    );
    when(requestContext.getUriInfo()).thenReturn(uriInfo);

    filter.filter(requestContext);

    // Capture what was set and return it
    when(requestContext.getProperty("metrics.startTime")).thenReturn(System.nanoTime());
    when(requestContext.getProperty("metrics.endpoint")).thenReturn(MONITORED_ENDPOINT);
    when(requestContext.getHeaderString(CLIENT_HEADER)).thenReturn("app1");

    filter.filter(requestContext, responseContext);

    // Should be recorded under the monitored endpoint, not the full path
    Timer timer = registry
      .find(METRIC_NAME)
      .tag("client", "app1")
      .tag("uri", MONITORED_ENDPOINT)
      .timer();
    assertEquals(1, timer.count());
  }

  private void recordRequest(String clientName, String endpoint) {
    var requestContext = mock(ContainerRequestContext.class);
    var responseContext = mock(ContainerResponseContext.class);
    var uriInfo = mock(UriInfo.class);

    when(uriInfo.getRequestUri()).thenReturn(URI.create(endpoint));
    when(requestContext.getUriInfo()).thenReturn(uriInfo);

    filter.filter(requestContext);

    // Simulate what the filter stores in the request context
    when(requestContext.getProperty("metrics.startTime")).thenReturn(System.nanoTime());
    when(requestContext.getProperty("metrics.endpoint")).thenReturn(endpoint);
    when(requestContext.getHeaderString(CLIENT_HEADER)).thenReturn(clientName);

    filter.filter(requestContext, responseContext);
  }
}
