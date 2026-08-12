package org.opentripplanner.standalone.configure;

import static com.google.common.truth.Truth.assertThat;

import org.junit.jupiter.api.Test;
import org.opentripplanner.street.graph.Graph;
import org.opentripplanner.transit.service.DefaultTransitService;
import org.opentripplanner.transit.service.TransitRepository;

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

  private final RecordingRequestScopedFactory factory = new RecordingRequestScopedFactory();
  private final DaggerGtfsGraphQLRequestContext context = new DaggerGtfsGraphQLRequestContext(
    factory.factory()
  );

  @Test
  void constructingTheContextTouchesNothing() {
    assertThat(factory.callCounts()).isEmpty();
  }

  @Test
  void routingService() {
    context.routingService();
    assertThat(factory.callCounts()).containsExactly("routingService", 1);
  }

  @Test
  void transitService() {
    context.transitService();
    assertThat(factory.callCounts()).containsExactly("transitService", 1);
  }

  @Test
  void transferService() {
    context.transferService();
    assertThat(factory.callCounts()).containsExactly("transferService", 1);
  }

  @Test
  void fareService() {
    context.fareService();
    assertThat(factory.callCounts()).containsExactly("fareService", 1);
  }

  @Test
  void vehicleRentalService() {
    context.vehicleRentalService();
    assertThat(factory.callCounts()).containsExactly("vehicleRentalService", 1);
  }

  @Test
  void vehicleParkingService() {
    context.vehicleParkingService();
    assertThat(factory.callCounts()).containsExactly("vehicleParkingService", 1);
  }

  @Test
  void realTimeVehicleService() {
    context.realTimeVehicleService();
    assertThat(factory.callCounts()).containsExactly("realtimeVehicleService", 1);
  }

  @Test
  void schema() {
    context.schema();
    assertThat(factory.callCounts()).containsExactly("gtfsSchema", 1);
  }

  @Test
  void defaultRouteRequest() {
    context.defaultRouteRequest();
    assertThat(factory.callCounts()).containsExactly("defaultRouteRequest", 1);
  }

  @Test
  void nearbyPlaceFinderOnlyTouchesLinkingContextFactory() {
    context.nearbyPlaceFinder();
    assertThat(factory.callCounts()).containsExactly("linkingContextFactory", 1);
  }

  @Test
  void nearbyPlaceFinderIsMemoized() {
    var first = context.nearbyPlaceFinder();
    var second = context.nearbyPlaceFinder();

    assertThat(second).isSameInstanceAs(first);
    assertThat(factory.callCount("linkingContextFactory")).isEqualTo(1);
  }

  @Test
  void nearbyStopFinderOnAGraphWithoutStreetsOnlyTouchesGraphAndTransitService() {
    factory.stub("graph", new Graph());
    factory.stub("transitService", new DefaultTransitService(new TransitRepository()));

    context.nearbyStopFinder();

    assertThat(factory.callCounts()).containsExactly("graph", 1, "transitService", 1);
  }

  @Test
  void nearbyStopFinderIsMemoized() {
    factory.stub("graph", new Graph());
    factory.stub("transitService", new DefaultTransitService(new TransitRepository()));

    var first = context.nearbyStopFinder();
    var second = context.nearbyStopFinder();

    assertThat(second).isSameInstanceAs(first);
    assertThat(factory.callCount("graph")).isEqualTo(1);
    assertThat(factory.callCount("transitService")).isEqualTo(1);
  }
}
