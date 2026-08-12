package org.opentripplanner.apis.gtfs;

import static com.google.common.truth.Truth.assertThat;

import graphql.schema.GraphQLSchema;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.opentripplanner._support.lazy.LazyCounter;
import org.opentripplanner.ext.fares.service.gtfs.v1.DefaultFareService;
import org.opentripplanner.place.NearbyPlaceFinder;
import org.opentripplanner.place.NearbyStopFinder;
import org.opentripplanner.place.nearbystopfinder.StraightLineNearbyStopFinder;
import org.opentripplanner.place.nearbystopfinder.StreetNearbyStopFinder;
import org.opentripplanner.place.placefinder.StreetNearbyPlaceFinder;
import org.opentripplanner.routing.api.RoutingService;
import org.opentripplanner.routing.api.request.RouteRequest;
import org.opentripplanner.routing.fares.FareService;
import org.opentripplanner.routing.impl.TransitAlertServiceImpl;
import org.opentripplanner.routing.linking.LinkingContextFactory;
import org.opentripplanner.routing.linking.VertexLinkerTestFactory;
import org.opentripplanner.routing.services.TransitAlertService;
import org.opentripplanner.service.realtimevehicles.RealtimeVehicleService;
import org.opentripplanner.service.realtimevehicles.internal.DefaultRealtimeVehicleRepository;
import org.opentripplanner.service.realtimevehicles.internal.DefaultRealtimeVehicleService;
import org.opentripplanner.service.realtimevehicles.internal.RealtimeVehicleRepositoryLifecycle;
import org.opentripplanner.service.vehicleparking.VehicleParkingService;
import org.opentripplanner.service.vehiclerental.VehicleRentalService;
import org.opentripplanner.standalone.api.TestServerContext;
import org.opentripplanner.street.graph.Graph;
import org.opentripplanner.transfer.regular.RegularTransferService;
import org.opentripplanner.transfer.regular.TransferRepository;
import org.opentripplanner.transfer.regular.TransferServiceTestFactory;
import org.opentripplanner.transfer.regular.internal.DefaultTransferRepository;
import org.opentripplanner.transfer.regular.internal.TransferIndex;
import org.opentripplanner.transit.service.DefaultTransitService;
import org.opentripplanner.transit.service.TransitRepository;
import org.opentripplanner.transit.service.TransitService;

/**
 * Verifies the {@link GtfsGraphQLRequestContext} contract documented on the interface: a service
 * is only ever constructed if some data fetcher actually needs it during a request, and never
 * more than once even if several data fetchers ask for it. Exercised through the real GraphQL
 * execution entry point ({@link GtfsGraphQLIndex}) with real queries, so the result reflects what
 * data fetchers actually touch — not an assumption about what they should touch.
 */
class GtfsGraphQLLazyServiceConstructionTest {

  @Test
  void feedsQueryOnlyConstructsTransitServiceAndTheAlwaysEagerOnes() {
    var probe = new Probe();

    var response = GtfsGraphQLIndex.getGraphQLResponse(
      "{ feeds { version } }",
      null,
      null,
      2000,
      2000,
      Locale.ENGLISH,
      probe.context,
      List.of()
    );

    assertThat(response.getStatus()).isEqualTo(200);

    // Touched by the query itself.
    assertThat(probe.transitService.constructions()).isEqualTo(1);
    // GtfsGraphQLIndex always resolves the schema and probes fareService up front (to decide
    // whether to register the itinerary fare data loader), regardless of what the query asks.
    assertThat(probe.schema.constructions()).isEqualTo(1);
    assertThat(probe.fareService.constructions()).isEqualTo(1);

    // Never touched by this query.
    assertThat(probe.routingService.constructions()).isEqualTo(0);
    assertThat(probe.transferService.constructions()).isEqualTo(0);
    assertThat(probe.vehicleRentalService.constructions()).isEqualTo(0);
    assertThat(probe.vehicleParkingService.constructions()).isEqualTo(0);
    assertThat(probe.realtimeVehicleService.constructions()).isEqualTo(0);
    assertThat(probe.nearbyPlaceFinder.constructions()).isEqualTo(0);
    assertThat(probe.nearbyStopFinder.constructions()).isEqualTo(0);
    assertThat(probe.defaultRouteRequest.constructions()).isEqualTo(0);
  }

  @Test
  void planConnectionQueryConstructsRoutingServiceExactlyOnce() {
    var probe = new Probe();
    var query = """
      {
        planConnection(
          origin: { location: { coordinate: { latitude: 60.199, longitude: 24.941 } } }
          destination: { location: { coordinate: { latitude: 60.169, longitude: 24.932 } } }
          dateTime: { earliestDeparture: "2024-01-01T10:00:00+02:00" }
          modes: { direct: [WALK] }
        ) {
          edges {
            node {
              start
              legs {
                mode
              }
            }
          }
        }
      }
      """;

    var response = GtfsGraphQLIndex.getGraphQLResponse(
      query,
      null,
      null,
      2000,
      2000,
      Locale.ENGLISH,
      probe.context,
      List.of()
    );

    assertThat(response.getStatus()).isEqualTo(200);

    // A planConnection query resolves many legs/edges in a real response, each of which may ask
    // the context for the same service again — that's expected. What matters is that no matter
    // how many times it's asked, it's only constructed once.
    assertThat(probe.routingService.constructions()).isEqualTo(1);
    assertThat(probe.defaultRouteRequest.constructions()).isEqualTo(1);

    // Services this query never touches.
    assertThat(probe.vehicleRentalService.constructions()).isEqualTo(0);
    assertThat(probe.vehicleParkingService.constructions()).isEqualTo(0);
    assertThat(probe.realtimeVehicleService.constructions()).isEqualTo(0);
    assertThat(probe.nearbyPlaceFinder.constructions()).isEqualTo(0);
    assertThat(probe.nearbyStopFinder.constructions()).isEqualTo(0);
  }

  /**
   * Builds a real (if minimal) set of dependencies and a {@link GtfsGraphQLRequestContext} whose
   * accessors are backed by {@link LazyCounter}s, so a test can inspect exactly how many times
   * each dependency was constructed after running a query.
   */
  private static final class Probe {

    final LazyCounter<RoutingService> routingService;
    final LazyCounter<TransitService> transitService;
    final LazyCounter<TransitAlertService> transitAlertService;
    final LazyCounter<RegularTransferService> transferService;
    final LazyCounter<FareService> fareService;
    final LazyCounter<VehicleRentalService> vehicleRentalService;
    final LazyCounter<VehicleParkingService> vehicleParkingService;
    final LazyCounter<RealtimeVehicleService> realtimeVehicleService;
    final LazyCounter<GraphQLSchema> schema;
    final LazyCounter<NearbyPlaceFinder> nearbyPlaceFinder;
    final LazyCounter<NearbyStopFinder> nearbyStopFinder;
    final LazyCounter<RouteRequest> defaultRouteRequest;
    final GtfsGraphQLRequestContext context;

    Probe() {
      var graph = new Graph();
      var transitRepository = new TransitRepository();
      TransferRepository transferRepository = new DefaultTransferRepository(new TransferIndex());
      var vertexLinker = VertexLinkerTestFactory.of(graph);

      transitService = new LazyCounter<>(() -> new DefaultTransitService(transitRepository));
      transferService = new LazyCounter<>(() ->
        TransferServiceTestFactory.transferService(transferRepository)
      );
      routingService = new LazyCounter<>(() ->
        TestServerContext.createRoutingService(graph, transitService.get(), transferRepository)
      );
      transitAlertService = new LazyCounter<>(TransitAlertServiceImpl::new);
      fareService = new LazyCounter<>(DefaultFareService::new);
      vehicleRentalService = new LazyCounter<>(TestServerContext::createVehicleRentalService);
      vehicleParkingService = new LazyCounter<>(TestServerContext::createVehicleParkingService);
      realtimeVehicleService = new LazyCounter<>(() ->
        new DefaultRealtimeVehicleService(
          new RealtimeVehicleRepositoryLifecycle().freeze(new DefaultRealtimeVehicleRepository()),
          transitService.get()
        )
      );
      schema = new LazyCounter<>(() ->
        SchemaFactory.createSchemaWithDefaultInjection(RouteRequest.defaultValue())
      );
      defaultRouteRequest = new LazyCounter<>(RouteRequest::defaultValue);
      LinkingContextFactory linkingContextFactory = TestServerContext.createLinkingContextFactory(
        graph,
        vertexLinker,
        transitService.get()
      );
      nearbyPlaceFinder = new LazyCounter<>(() ->
        new StreetNearbyPlaceFinder(linkingContextFactory)
      );
      nearbyStopFinder = new LazyCounter<>(() ->
        graph.hasStreets
          ? StreetNearbyStopFinder.of(linkingContextFactory).build()
          : new StraightLineNearbyStopFinder(transitService.get()::findRegularStopsByBoundingBox)
      );

      context = new GtfsGraphQLRequestContext() {
        @Override
        public RoutingService routingService() {
          return routingService.get();
        }

        @Override
        public TransitService transitService() {
          return transitService.get();
        }

        @Override
        public TransitAlertService transitAlertService() {
          return transitAlertService.get();
        }

        @Override
        public RegularTransferService transferService() {
          return transferService.get();
        }

        @Override
        public FareService fareService() {
          return fareService.get();
        }

        @Override
        public VehicleRentalService vehicleRentalService() {
          return vehicleRentalService.get();
        }

        @Override
        public VehicleParkingService vehicleParkingService() {
          return vehicleParkingService.get();
        }

        @Override
        public RealtimeVehicleService realTimeVehicleService() {
          return realtimeVehicleService.get();
        }

        @Override
        public GraphQLSchema schema() {
          return schema.get();
        }

        @Override
        public NearbyPlaceFinder nearbyPlaceFinder() {
          return nearbyPlaceFinder.get();
        }

        @Override
        public NearbyStopFinder nearbyStopFinder() {
          return nearbyStopFinder.get();
        }

        @Override
        public RouteRequest defaultRouteRequest() {
          return defaultRouteRequest.get();
        }
      };
    }
  }
}
