package org.opentripplanner.standalone.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.core.UriInfo;
import java.net.URI;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ClientRequestMetricsFilterTest {

  private static final String CLIENT_HEADER = "et-client-name";
  private static final String MONITORED_ENDPOINT = "/transmodel/v3";
  private static final Set<String> MONITORED_ENDPOINTS = Set.of(MONITORED_ENDPOINT, "/gtfs/v1/");
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
  void recordsMetricForKnownClient() throws Exception {
    var requestContext = mock(ContainerRequestContext.class);
    var responseContext = mock(ContainerResponseContext.class);
    var uriInfo = mock(UriInfo.class);

    when(uriInfo.getRequestUri()).thenReturn(URI.create(MONITORED_ENDPOINT));
    when(requestContext.getUriInfo()).thenReturn(uriInfo);

    // Capture the property being set
    ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<Object> valueCaptor = ArgumentCaptor.forClass(Object.class);

    // Request filter stores start time
    filter.filter(requestContext);
    verify(requestContext).setProperty(keyCaptor.capture(), valueCaptor.capture());

    // Simulate some processing time
    Thread.sleep(10);

    // Setup for response filter
    when(requestContext.getProperty(keyCaptor.getValue())).thenReturn(valueCaptor.getValue());
    when(requestContext.getHeaderString(CLIENT_HEADER)).thenReturn("app1");

    // Response filter records metric
    filter.filter(requestContext, responseContext);

    Timer timer = registry.find(METRIC_NAME).tag("client", "app1").timer();
    assertNotNull(timer, "Timer should exist for known client");
    assertEquals(1, timer.count());
    // Should have recorded at least 10ms
    assert timer.totalTime(TimeUnit.MILLISECONDS) >= 10;
  }

  @Test
  void matchesClientNameCaseInsensitively() {
    // Send request with uppercase client name "APP1" which should match configured "app1"
    recordRequest("APP1");
    // Send request with mixed case "App1"
    recordRequest("App1");
    // Send request with lowercase "app1"
    recordRequest("app1");

    // All three should be recorded under the lowercase "app1" tag
    Timer timer = registry.find(METRIC_NAME).tag("client", "app1").timer();
    assertNotNull(timer, "Timer should exist for case-insensitive matched client");
    assertEquals(3, timer.count());
  }

  @Test
  void recordsMetricForUnknownClientAsOther() {
    var requestContext = mock(ContainerRequestContext.class);
    var responseContext = mock(ContainerResponseContext.class);
    var uriInfo = mock(UriInfo.class);

    when(uriInfo.getRequestUri()).thenReturn(URI.create(MONITORED_ENDPOINT));
    when(requestContext.getUriInfo()).thenReturn(uriInfo);

    ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<Object> valueCaptor = ArgumentCaptor.forClass(Object.class);

    filter.filter(requestContext);
    verify(requestContext).setProperty(keyCaptor.capture(), valueCaptor.capture());

    when(requestContext.getProperty(keyCaptor.getValue())).thenReturn(valueCaptor.getValue());
    when(requestContext.getHeaderString(CLIENT_HEADER)).thenReturn("unknown-app");

    filter.filter(requestContext, responseContext);

    Timer timer = registry.find(METRIC_NAME).tag("client", "other").timer();
    assertNotNull(timer, "Timer should exist with 'other' tag for unknown client");
    assertEquals(1, timer.count());
  }

  @Test
  void recordsMetricForMissingHeaderAsOther() {
    var requestContext = mock(ContainerRequestContext.class);
    var responseContext = mock(ContainerResponseContext.class);
    var uriInfo = mock(UriInfo.class);

    when(uriInfo.getRequestUri()).thenReturn(URI.create(MONITORED_ENDPOINT));
    when(requestContext.getUriInfo()).thenReturn(uriInfo);

    ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<Object> valueCaptor = ArgumentCaptor.forClass(Object.class);

    filter.filter(requestContext);
    verify(requestContext).setProperty(keyCaptor.capture(), valueCaptor.capture());

    when(requestContext.getProperty(keyCaptor.getValue())).thenReturn(valueCaptor.getValue());
    when(requestContext.getHeaderString(CLIENT_HEADER)).thenReturn(null);

    filter.filter(requestContext, responseContext);

    Timer timer = registry.find(METRIC_NAME).tag("client", "other").timer();
    assertNotNull(timer, "Timer should exist with 'other' tag for missing header");
    assertEquals(1, timer.count());
  }

  @Test
  void handlesMultipleClients() {
    // First request from app1
    recordRequest("app1");
    // Second request from app2
    recordRequest("app2");
    // Third request from unknown
    recordRequest("unknown");
    // Fourth request from app1 again
    recordRequest("app1");

    assertEquals(2, registry.find(METRIC_NAME).tag("client", "app1").timer().count());
    assertEquals(1, registry.find(METRIC_NAME).tag("client", "app2").timer().count());
    assertEquals(1, registry.find(METRIC_NAME).tag("client", "other").timer().count());
  }

  private void recordRequest(String clientName) {
    var requestContext = mock(ContainerRequestContext.class);
    var responseContext = mock(ContainerResponseContext.class);
    var uriInfo = mock(UriInfo.class);

    when(uriInfo.getRequestUri()).thenReturn(URI.create(MONITORED_ENDPOINT));
    when(requestContext.getUriInfo()).thenReturn(uriInfo);

    ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<Object> valueCaptor = ArgumentCaptor.forClass(Object.class);

    filter.filter(requestContext);
    verify(requestContext).setProperty(keyCaptor.capture(), valueCaptor.capture());

    when(requestContext.getProperty(keyCaptor.getValue())).thenReturn(valueCaptor.getValue());
    when(requestContext.getHeaderString(CLIENT_HEADER)).thenReturn(clientName);

    filter.filter(requestContext, responseContext);
  }

  @Test
  void reusesTimerForSameClientAndUri() {
    // Record multiple requests from the same client to the same URI
    recordRequest("app1");
    recordRequest("app1");
    recordRequest("app1");

    // All requests should use the same timer instance
    Timer timer = registry
      .find(METRIC_NAME)
      .tag("client", "app1")
      .tag("uri", MONITORED_ENDPOINT)
      .timer();
    assertNotNull(timer);
    assertEquals(3, timer.count());

    // Only one timer should exist for this client/uri combination
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

    filter.filter(requestContext);
    filter.filter(requestContext, responseContext);

    // No timer should be created for non-monitored endpoint
    assertNull(registry.find(METRIC_NAME).timer());
  }

  @Test
  void recordsMetricsForMultipleMonitoredEndpoints() {
    // Request to first endpoint
    recordRequestToEndpoint("app1", MONITORED_ENDPOINT);
    // Request to second endpoint
    recordRequestToEndpoint("app1", "/gtfs/v1/");

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
      .tag("uri", "/gtfs/v1/")
      .timer();
    assertNotNull(gtfsTimer);
    assertEquals(1, gtfsTimer.count());
  }

  private void recordRequestToEndpoint(String clientName, String endpoint) {
    var requestContext = mock(ContainerRequestContext.class);
    var responseContext = mock(ContainerResponseContext.class);
    var uriInfo = mock(UriInfo.class);

    when(uriInfo.getRequestUri()).thenReturn(URI.create(endpoint));
    when(requestContext.getUriInfo()).thenReturn(uriInfo);

    ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<Object> valueCaptor = ArgumentCaptor.forClass(Object.class);

    filter.filter(requestContext);
    verify(requestContext).setProperty(keyCaptor.capture(), valueCaptor.capture());

    when(requestContext.getProperty(keyCaptor.getValue())).thenReturn(valueCaptor.getValue());
    when(requestContext.getHeaderString(CLIENT_HEADER)).thenReturn(clientName);

    filter.filter(requestContext, responseContext);
  }
}
