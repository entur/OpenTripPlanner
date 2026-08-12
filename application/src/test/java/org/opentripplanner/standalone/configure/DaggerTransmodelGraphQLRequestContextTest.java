package org.opentripplanner.standalone.configure;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.opentripplanner.street.graph.Graph;
import org.opentripplanner.transit.service.TransitService;

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

  private final RequestScopedFactory factory = mock(RequestScopedFactory.class);
  private final DaggerTransmodelGraphQLRequestContext context =
    new DaggerTransmodelGraphQLRequestContext(factory);

  @Test
  void constructingTheContextTouchesNothing() {
    verifyNoInteractions(factory);
  }

  @Test
  void getRoutingService() {
    context.getRoutingService();
    verify(factory).routingService();
    verifyNoMoreInteractions(factory);
  }

  @Test
  void getTransitService() {
    context.getTransitService();
    verify(factory).transitService();
    verifyNoMoreInteractions(factory);
  }

  @Test
  void getEmpiricalDelayService() {
    context.getEmpiricalDelayService();
    verify(factory).empiricalDelayService();
    verifyNoMoreInteractions(factory);
  }

  @Test
  void getDefaultRouteRequest() {
    context.getDefaultRouteRequest();
    verify(factory).defaultRouteRequest();
    verifyNoMoreInteractions(factory);
  }

  @Test
  void getVehicleRentalService() {
    context.getVehicleRentalService();
    verify(factory).vehicleRentalService();
    verifyNoMoreInteractions(factory);
  }

  @Test
  void getVehicleParkingService() {
    context.getVehicleParkingService();
    verify(factory).vehicleParkingService();
    verifyNoMoreInteractions(factory);
  }

  @Test
  void getGraph() {
    context.getGraph();
    verify(factory).graph();
    verifyNoMoreInteractions(factory);
  }

  @Test
  void getTransferService() {
    context.getTransferService();
    verify(factory).transferService();
    verifyNoMoreInteractions(factory);
  }

  @Test
  void getStreetDetailsService() {
    context.getStreetDetailsService();
    verify(factory).streetDetailsService();
    verifyNoMoreInteractions(factory);
  }

  @Test
  void getLinkingContextFactory() {
    context.getLinkingContextFactory();
    verify(factory).linkingContextFactory();
    verifyNoMoreInteractions(factory);
  }

  @Test
  void getStreetLimitationParametersService() {
    context.getStreetLimitationParametersService();
    verify(factory).streetLimitationParametersService();
    verifyNoMoreInteractions(factory);
  }

  @Test
  void getNearbyPlaceFinderOnlyTouchesLinkingContextFactory() {
    context.getNearbyPlaceFinder();
    verify(factory).linkingContextFactory();
    verifyNoMoreInteractions(factory);
  }

  @Test
  void getNearbyStopFinderOnAGraphWithoutStreetsOnlyTouchesGraphAndTransitService() {
    when(factory.graph()).thenReturn(new Graph());
    when(factory.transitService()).thenReturn(mock(TransitService.class));

    context.getNearbyStopFinder();

    verify(factory).graph();
    verify(factory).transitService();
    verifyNoMoreInteractions(factory);
  }
}
