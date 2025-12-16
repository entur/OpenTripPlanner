package org.opentripplanner.standalone.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opentripplanner.standalone.server.ClientRequestMetricsFilter.METRIC_NAME;
import static org.opentripplanner.standalone.server.ClientRequestMetricsFilter.disabled;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ClientRequestMetricsFilterTest {

  private static final String CLIENT_HEADER = "et-client-name";
  private SimpleMeterRegistry registry;
  private ClientRequestMetricsFilter filter;

  @BeforeEach
  void setUp() {
    registry = new SimpleMeterRegistry();
    filter = new ClientRequestMetricsFilter(
      CLIENT_HEADER,
      Set.of("app1", "app2", "web-client"),
      registry
    );
  }

  @Test
  void recordsMetricForKnownClient() throws Exception {
    var requestContext = mock(ContainerRequestContext.class);
    var responseContext = mock(ContainerResponseContext.class);
    var uriInfo = mock(UriInfo.class);

    when(uriInfo.getRequestUri()).thenReturn(URI.create("/test/path"));
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

    when(uriInfo.getRequestUri()).thenReturn(URI.create("/test/path"));
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

    when(uriInfo.getRequestUri()).thenReturn(URI.create("/test/path"));
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

    when(uriInfo.getRequestUri()).thenReturn(URI.create("/test/path"));
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
  void disabledFilterDoesNotRecordMetrics() throws Exception {
    // Create disabled filter (empty known clients set means disabled)
    var disabledFilter = disabled();

    var requestContext = mock(ContainerRequestContext.class);
    var responseContext = mock(ContainerResponseContext.class);

    disabledFilter.filter(requestContext);
    disabledFilter.filter(requestContext, responseContext);

    // No timers should be registered
    assertNull(registry.find(METRIC_NAME).timer());
  }
}
