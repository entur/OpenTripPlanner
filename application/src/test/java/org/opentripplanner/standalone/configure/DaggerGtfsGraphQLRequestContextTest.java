package org.opentripplanner.standalone.configure;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.opentripplanner.street.graph.Graph;
import org.opentripplanner.transit.service.TransitService;

/**
 * Verifies that {@link DaggerGtfsGraphQLRequestContext} — the production {@link
 * org.opentripplanner.apis.gtfs.GtfsGraphQLRequestContext} — delegates each accessor to exactly
 * the {@link RequestScopedFactory} bindings it needs, and nothing else. Combined with Dagger's
 * own {@code @HttpRequestScoped} memoization (already covered by {@link
 * RequestScopedFactoryTest}), this is what makes "lazy, at most once per request" true for the
 * real, wired-up context: touching one accessor here never has a side effect on any other
 * binding.
 */
class DaggerGtfsGraphQLRequestContextTest {

  private final RequestScopedFactory factory = mock(RequestScopedFactory.class);
  private final DaggerGtfsGraphQLRequestContext context = new DaggerGtfsGraphQLRequestContext(
    factory
  );

  @Test
  void constructingTheContextTouchesNothing() {
    verifyNoInteractions(factory);
  }

  @Test
  void routingService() {
    context.routingService();
    verify(factory).routingService();
    verifyNoMoreInteractions(factory);
  }

  @Test
  void transitService() {
    context.transitService();
    verify(factory).transitService();
    verifyNoMoreInteractions(factory);
  }

  @Test
  void transferService() {
    context.transferService();
    verify(factory).transferService();
    verifyNoMoreInteractions(factory);
  }

  @Test
  void fareService() {
    context.fareService();
    verify(factory).fareService();
    verifyNoMoreInteractions(factory);
  }

  @Test
  void vehicleRentalService() {
    context.vehicleRentalService();
    verify(factory).vehicleRentalService();
    verifyNoMoreInteractions(factory);
  }

  @Test
  void vehicleParkingService() {
    context.vehicleParkingService();
    verify(factory).vehicleParkingService();
    verifyNoMoreInteractions(factory);
  }

  @Test
  void realTimeVehicleService() {
    context.realTimeVehicleService();
    verify(factory).realtimeVehicleService();
    verifyNoMoreInteractions(factory);
  }

  @Test
  void schema() {
    context.schema();
    verify(factory).gtfsSchema();
    verifyNoMoreInteractions(factory);
  }

  @Test
  void defaultRouteRequest() {
    context.defaultRouteRequest();
    verify(factory).defaultRouteRequest();
    verifyNoMoreInteractions(factory);
  }

  @Test
  void nearbyPlaceFinderOnlyTouchesLinkingContextFactory() {
    context.nearbyPlaceFinder();
    verify(factory).linkingContextFactory();
    verifyNoMoreInteractions(factory);
  }

  @Test
  void nearbyPlaceFinderIsMemoized() {
    var first = context.nearbyPlaceFinder();
    var second = context.nearbyPlaceFinder();

    assertThat(second).isSameInstanceAs(first);
    verify(factory, times(1)).linkingContextFactory();
  }

  @Test
  void nearbyStopFinderOnAGraphWithoutStreetsOnlyTouchesGraphAndTransitService() {
    when(factory.graph()).thenReturn(new Graph());
    when(factory.transitService()).thenReturn(mock(TransitService.class));

    context.nearbyStopFinder();

    verify(factory).graph();
    verify(factory).transitService();
    verifyNoMoreInteractions(factory);
  }

  @Test
  void nearbyStopFinderIsMemoized() {
    when(factory.graph()).thenReturn(new Graph());
    when(factory.transitService()).thenReturn(mock(TransitService.class));

    var first = context.nearbyStopFinder();
    var second = context.nearbyStopFinder();

    assertThat(second).isSameInstanceAs(first);
    verify(factory, times(1)).graph();
    verify(factory, times(1)).transitService();
  }
}
