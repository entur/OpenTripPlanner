package org.opentripplanner.apis.transmodel;

import static com.google.common.truth.Truth.assertThat;

import jakarta.ws.rs.core.Response;
import java.util.List;
import javax.annotation.Nullable;
import org.junit.jupiter.api.Test;
import org.opentripplanner._support.lazy.LazyCounter;
import org.opentripplanner._support.time.ZoneIds;
import org.opentripplanner.api.model.transit.DefaultFeedIdMapper;
import org.opentripplanner.apis.support.graphql.injectdoc.ApiDocumentationProfile;
import org.opentripplanner.ext.empiricaldelay.EmpiricalDelayService;
import org.opentripplanner.place.NearbyPlaceFinder;
import org.opentripplanner.place.NearbyStopFinder;
import org.opentripplanner.place.nearbystopfinder.StraightLineNearbyStopFinder;
import org.opentripplanner.place.nearbystopfinder.StreetNearbyStopFinder;
import org.opentripplanner.place.placefinder.StreetNearbyPlaceFinder;
import org.opentripplanner.routing.algorithm.raptoradapter.transit.TransitTuningParametersTestFactory;
import org.opentripplanner.routing.api.RoutingService;
import org.opentripplanner.routing.api.request.RouteRequest;
import org.opentripplanner.routing.impl.TransitAlertServiceImpl;
import org.opentripplanner.routing.linking.LinkingContextFactory;
import org.opentripplanner.routing.linking.VertexLinkerTestFactory;
import org.opentripplanner.routing.services.TransitAlertService;
import org.opentripplanner.service.streetdetails.StreetDetailsService;
import org.opentripplanner.service.vehicleparking.VehicleParkingService;
import org.opentripplanner.service.vehiclerental.VehicleRentalService;
import org.opentripplanner.standalone.api.TestServerContext;
import org.opentripplanner.street.graph.Graph;
import org.opentripplanner.street.service.StreetLimitationParametersService;
import org.opentripplanner.transfer.regular.RegularTransferService;
import org.opentripplanner.transfer.regular.TransferRepository;
import org.opentripplanner.transfer.regular.TransferServiceTestFactory;
import org.opentripplanner.transfer.regular.internal.DefaultTransferRepository;
import org.opentripplanner.transfer.regular.internal.TransferIndex;
import org.opentripplanner.transit.service.DefaultTransitService;
import org.opentripplanner.transit.service.TransitRepository;
import org.opentripplanner.transit.service.TransitService;

/**
 * Verifies the {@link TransmodelGraphQLRequestContext} contract documented on the interface: a
 * service is only ever constructed if some data fetcher actually needs it during a request, and
 * never more than once even if several data fetchers ask for it. Exercised through the real
 * GraphQL execution entry point ({@link TransmodelGraph}) with real queries, so the result
 * reflects what data fetchers actually touch — not an assumption about what they should touch.
 */
class TransmodelGraphQLLazyServiceConstructionTest {

  @Test
  void authoritiesQueryOnlyConstructsTransitService() {
    var probe = new Probe();

    var response = execute(probe, "{ authorities { id } }");

    assertThat(response.getStatus()).isEqualTo(200);

    assertThat(probe.transitService.constructions()).isEqualTo(1);

    assertThat(probe.routingService.constructions()).isEqualTo(0);
    assertThat(probe.defaultRouteRequest.constructions()).isEqualTo(0);
    assertThat(probe.transferService.constructions()).isEqualTo(0);
    assertThat(probe.vehicleRentalService.constructions()).isEqualTo(0);
    assertThat(probe.vehicleParkingService.constructions()).isEqualTo(0);
    assertThat(probe.streetDetailsService.constructions()).isEqualTo(0);
    assertThat(probe.streetLimitationParametersService.constructions()).isEqualTo(0);
    assertThat(probe.empiricalDelayService.constructions()).isEqualTo(0);
    assertThat(probe.nearbyPlaceFinder.constructions()).isEqualTo(0);
    assertThat(probe.nearbyStopFinder.constructions()).isEqualTo(0);
    assertThat(probe.graph.constructions()).isEqualTo(0);
  }

  @Test
  void tripQueryConstructsRoutingServiceExactlyOnce() {
    var probe = new Probe();
    var query = """
      {
        trip(
          from: { coordinates: { latitude: 60.199, longitude: 24.941 } }
          to: { coordinates: { latitude: 60.169, longitude: 24.932 } }
          dateTime: "2024-01-01T10:00:00+02:00"
        ) {
          dateTime
        }
      }
      """;

    var response = execute(probe, query);

    assertThat(response.getStatus()).isEqualTo(200);

    // A trip response may resolve several fields that each ask the context for the routing
    // service again — that's expected. What matters is that no matter how many times it's asked,
    // it's only constructed once.
    assertThat(probe.routingService.constructions()).isEqualTo(1);
    assertThat(probe.defaultRouteRequest.constructions()).isEqualTo(1);

    // Services this query never touches.
    assertThat(probe.vehicleRentalService.constructions()).isEqualTo(0);
    assertThat(probe.vehicleParkingService.constructions()).isEqualTo(0);
    assertThat(probe.empiricalDelayService.constructions()).isEqualTo(0);
    assertThat(probe.nearbyPlaceFinder.constructions()).isEqualTo(0);
    assertThat(probe.nearbyStopFinder.constructions()).isEqualTo(0);
  }

  private static Response execute(Probe probe, String query) {
    var schema = new TransmodelGraphQLSchemaFactory(
      RouteRequest.defaultValue(),
      ZoneIds.OSLO,
      TransitTuningParametersTestFactory.forTest(),
      new DefaultFeedIdMapper(),
      ApiDocumentationProfile.DEFAULT
    ).create();

    return new TransmodelGraph(schema).executeGraphQL(
      query,
      probe.context,
      null,
      null,
      2000,
      List.of()
    );
  }

  /**
   * Builds a real (if minimal) set of dependencies and a {@link TransmodelGraphQLRequestContext}
   * whose accessors are backed by {@link LazyCounter}s, so a test can inspect exactly how many
   * times each dependency was constructed after running a query.
   */
  private static final class Probe {

    final LazyCounter<RoutingService> routingService;
    final LazyCounter<TransitService> transitService;
    final LazyCounter<TransitAlertService> transitAlertService;
    final LazyCounter<EmpiricalDelayService> empiricalDelayService;
    final LazyCounter<RouteRequest> defaultRouteRequest;
    final LazyCounter<VehicleRentalService> vehicleRentalService;
    final LazyCounter<VehicleParkingService> vehicleParkingService;
    final LazyCounter<Graph> graph;
    final LazyCounter<RegularTransferService> transferService;
    final LazyCounter<StreetDetailsService> streetDetailsService;
    final LazyCounter<LinkingContextFactory> linkingContextFactory;
    final LazyCounter<StreetLimitationParametersService> streetLimitationParametersService;
    final LazyCounter<NearbyPlaceFinder> nearbyPlaceFinder;
    final LazyCounter<NearbyStopFinder> nearbyStopFinder;
    final TransmodelGraphQLRequestContext context;

    Probe() {
      var realGraph = new Graph();
      var transitRepository = new TransitRepository();
      TransferRepository transferRepository = new DefaultTransferRepository(new TransferIndex());
      var vertexLinker = VertexLinkerTestFactory.of(realGraph);

      graph = new LazyCounter<>(() -> realGraph);
      transitService = new LazyCounter<>(() -> new DefaultTransitService(transitRepository));
      transferService = new LazyCounter<>(() ->
        TransferServiceTestFactory.transferService(transferRepository)
      );
      routingService = new LazyCounter<>(() ->
        TestServerContext.createRoutingService(realGraph, transitService.get(), transferRepository)
      );
      transitAlertService = new LazyCounter<>(TransitAlertServiceImpl::new);
      empiricalDelayService = new LazyCounter<>(() -> null);
      defaultRouteRequest = new LazyCounter<>(RouteRequest::defaultValue);
      vehicleRentalService = new LazyCounter<>(TestServerContext::createVehicleRentalService);
      vehicleParkingService = new LazyCounter<>(TestServerContext::createVehicleParkingService);
      streetDetailsService = new LazyCounter<>(TestServerContext::createStreetDetailsService);
      streetLimitationParametersService = new LazyCounter<>(
        TestServerContext::createStreetLimitationParametersService
      );
      linkingContextFactory = new LazyCounter<>(() ->
        TestServerContext.createLinkingContextFactory(realGraph, vertexLinker, transitService.get())
      );
      nearbyPlaceFinder = new LazyCounter<>(() ->
        new StreetNearbyPlaceFinder(linkingContextFactory.get())
      );
      nearbyStopFinder = new LazyCounter<>(() ->
        realGraph.hasStreets
          ? StreetNearbyStopFinder.of(linkingContextFactory.get()).build()
          : new StraightLineNearbyStopFinder(transitService.get()::findRegularStopsByBoundingBox)
      );

      context = new TransmodelGraphQLRequestContext() {
        @Override
        public RoutingService getRoutingService() {
          return routingService.get();
        }

        @Override
        public TransitService getTransitService() {
          return transitService.get();
        }

        @Override
        public TransitAlertService getTransitAlertService() {
          return transitAlertService.get();
        }

        @Nullable
        @Override
        public EmpiricalDelayService getEmpiricalDelayService() {
          return empiricalDelayService.get();
        }

        @Override
        public RouteRequest getDefaultRouteRequest() {
          return defaultRouteRequest.get();
        }

        @Override
        public VehicleRentalService getVehicleRentalService() {
          return vehicleRentalService.get();
        }

        @Override
        public VehicleParkingService getVehicleParkingService() {
          return vehicleParkingService.get();
        }

        @Override
        public Graph getGraph() {
          return graph.get();
        }

        @Override
        public RegularTransferService getTransferService() {
          return transferService.get();
        }

        @Override
        public StreetDetailsService getStreetDetailsService() {
          return streetDetailsService.get();
        }

        @Override
        public LinkingContextFactory getLinkingContextFactory() {
          return linkingContextFactory.get();
        }

        @Override
        public StreetLimitationParametersService getStreetLimitationParametersService() {
          return streetLimitationParametersService.get();
        }

        @Override
        public NearbyPlaceFinder getNearbyPlaceFinder() {
          return nearbyPlaceFinder.get();
        }

        @Override
        public NearbyStopFinder getNearbyStopFinder() {
          return nearbyStopFinder.get();
        }
      };
    }
  }
}
