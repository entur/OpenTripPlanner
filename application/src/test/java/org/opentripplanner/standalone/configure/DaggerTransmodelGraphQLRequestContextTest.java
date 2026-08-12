package org.opentripplanner.standalone.configure;

import static com.google.common.truth.Truth.assertThat;

import org.junit.jupiter.api.Test;
import org.opentripplanner.street.graph.Graph;
import org.opentripplanner.transit.service.DefaultTransitService;
import org.opentripplanner.transit.service.TransitRepository;

/**
 * Verifies that {@link DaggerTransmodelGraphQLRequestContext} — the production {@link
 * org.opentripplanner.apis.transmodel.TransmodelGraphQLRequestContext} — delegates each accessor
 * to exactly the {@link RequestScopedFactory} bindings it needs, and nothing else. Combined with
 * Dagger's own {@code @HttpRequestScoped} memoization (already covered by {@link
 * RequestScopedFactoryTest}), this is what makes "lazy, at most once per request" true for the
 * real, wired-up context: touching one accessor here never has a side effect on any other
 * binding.
 */
class DaggerTransmodelGraphQLRequestContextTest {

  private final RecordingRequestScopedFactory factory = new RecordingRequestScopedFactory();
  private final DaggerTransmodelGraphQLRequestContext context =
    new DaggerTransmodelGraphQLRequestContext(factory.factory());

  @Test
  void constructingTheContextTouchesNothing() {
    assertThat(factory.callCounts()).isEmpty();
  }

  @Test
  void getRoutingService() {
    context.getRoutingService();
    assertThat(factory.callCounts()).containsExactly("routingService", 1);
  }

  @Test
  void getTransitService() {
    context.getTransitService();
    assertThat(factory.callCounts()).containsExactly("transitService", 1);
  }

  @Test
  void getEmpiricalDelayService() {
    context.getEmpiricalDelayService();
    assertThat(factory.callCounts()).containsExactly("empiricalDelayService", 1);
  }

  @Test
  void getDefaultRouteRequest() {
    context.getDefaultRouteRequest();
    assertThat(factory.callCounts()).containsExactly("defaultRouteRequest", 1);
  }

  @Test
  void getVehicleRentalService() {
    context.getVehicleRentalService();
    assertThat(factory.callCounts()).containsExactly("vehicleRentalService", 1);
  }

  @Test
  void getVehicleParkingService() {
    context.getVehicleParkingService();
    assertThat(factory.callCounts()).containsExactly("vehicleParkingService", 1);
  }

  @Test
  void getGraph() {
    context.getGraph();
    assertThat(factory.callCounts()).containsExactly("graph", 1);
  }

  @Test
  void getTransferService() {
    context.getTransferService();
    assertThat(factory.callCounts()).containsExactly("transferService", 1);
  }

  @Test
  void getStreetDetailsService() {
    context.getStreetDetailsService();
    assertThat(factory.callCounts()).containsExactly("streetDetailsService", 1);
  }

  @Test
  void getLinkingContextFactory() {
    context.getLinkingContextFactory();
    assertThat(factory.callCounts()).containsExactly("linkingContextFactory", 1);
  }

  @Test
  void getStreetLimitationParametersService() {
    context.getStreetLimitationParametersService();
    assertThat(factory.callCounts()).containsExactly("streetLimitationParametersService", 1);
  }

  @Test
  void getNearbyPlaceFinderOnlyTouchesLinkingContextFactory() {
    context.getNearbyPlaceFinder();
    assertThat(factory.callCounts()).containsExactly("linkingContextFactory", 1);
  }

  @Test
  void getNearbyPlaceFinderIsMemoized() {
    var first = context.getNearbyPlaceFinder();
    var second = context.getNearbyPlaceFinder();

    assertThat(second).isSameInstanceAs(first);
    assertThat(factory.callCount("linkingContextFactory")).isEqualTo(1);
  }

  @Test
  void getNearbyStopFinderOnAGraphWithoutStreetsOnlyTouchesGraphAndTransitService() {
    factory.stub("graph", new Graph());
    factory.stub("transitService", new DefaultTransitService(new TransitRepository()));

    context.getNearbyStopFinder();

    assertThat(factory.callCounts()).containsExactly("graph", 1, "transitService", 1);
  }

  @Test
  void getNearbyStopFinderIsMemoized() {
    factory.stub("graph", new Graph());
    factory.stub("transitService", new DefaultTransitService(new TransitRepository()));

    var first = context.getNearbyStopFinder();
    var second = context.getNearbyStopFinder();

    assertThat(second).isSameInstanceAs(first);
    assertThat(factory.callCount("graph")).isEqualTo(1);
    assertThat(factory.callCount("transitService")).isEqualTo(1);
  }
}
