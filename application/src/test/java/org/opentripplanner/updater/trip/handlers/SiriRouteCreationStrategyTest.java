package org.opentripplanner.updater.trip.handlers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.transit.model._data.FeedScopedIdForTestFactory;
import org.opentripplanner.transit.model._data.TransitTestEnvironment;
import org.opentripplanner.transit.model._data.TripInput;
import org.opentripplanner.transit.model.basic.TransitMode;
import org.opentripplanner.transit.service.TransitEditorService;
import org.opentripplanner.updater.spi.UpdateError;
import org.opentripplanner.updater.trip.model.TripCreationInfo;
import org.rutebanken.netex.model.BusSubmodeEnumeration;
import org.rutebanken.netex.model.RailSubmodeEnumeration;

class SiriRouteCreationStrategyTest {

  private static final String FEED_ID = FeedScopedIdForTestFactory.FEED_ID;
  private static final FeedScopedId TRIP_ID = new FeedScopedId(FEED_ID, "new-trip");
  private static final FeedScopedId ROUTE_ID = new FeedScopedId(FEED_ID, "route1");
  private static final FeedScopedId OPERATOR_ID = new FeedScopedId(FEED_ID, "operator1");

  private TransitEditorService transitService;
  private SiriRouteCreationStrategy strategy;

  @BeforeEach
  void setUp() {
    var builder = TransitTestEnvironment.of().addStops("A", "B", "C");

    var operator = builder.operator("operator1");
    var route = builder.route("route1", operator);

    // Add a RAIL route for replacement submode tests
    var railRoute = builder.route("rail-route", r -> r.withMode(TransitMode.RAIL));

    var stopA = builder.stop("A");
    var stopB = builder.stop("B");
    var stopC = builder.stop("C");

    var env = builder
      .addTrip(
        TripInput.of("trip1")
          .withRoute(route)
          .addStop(stopA, "10:00")
          .addStop(stopB, "10:30")
          .addStop(stopC, "11:00")
      )
      .addTrip(
        TripInput.of("rail-trip")
          .withRoute(railRoute)
          .addStop(stopA, "12:00")
          .addStop(stopB, "12:30")
          .addStop(stopC, "13:00")
      )
      .build();

    transitService = (TransitEditorService) env.transitService();
    strategy = new SiriRouteCreationStrategy(FEED_ID);
  }

  @Test
  void returnsExistingRouteWhenFound() {
    var info = TripCreationInfo.builder(TRIP_ID).withRouteId(ROUTE_ID).build();

    var result = strategy.resolveOrCreateRoute(info, transitService);

    assertTrue(result.isSuccess());
    assertFalse(result.successValue().isNewRoute());
    assertEquals(ROUTE_ID, result.successValue().route().getId());
  }

  @Test
  void createsRouteWithOperatorAgencyResolution() {
    var newRouteId = new FeedScopedId(FEED_ID, "new-route");
    var info = TripCreationInfo.builder(TRIP_ID)
      .withRouteId(newRouteId)
      .withOperatorId(OPERATOR_ID)
      .withMode(TransitMode.BUS)
      .withShortName("B1")
      .build();

    var result = strategy.resolveOrCreateRoute(info, transitService);

    assertTrue(result.isSuccess());
    assertTrue(result.successValue().isNewRoute());
    var route = result.successValue().route();
    assertEquals(newRouteId, route.getId());
    assertEquals(TransitMode.BUS, route.getMode());
    assertEquals("B1", route.getShortName());
    assertNotNull(route.getAgency());
    assertNotNull(route.getOperator());
    assertEquals(OPERATOR_ID, route.getOperator().getId());
  }

  @Test
  void createsRouteWithReplacedRouteAgencyFallback() {
    var newRouteId = new FeedScopedId(FEED_ID, "new-route");
    var info = TripCreationInfo.builder(TRIP_ID)
      .withRouteId(newRouteId)
      .withMode(TransitMode.BUS)
      .withReplacedRouteId(ROUTE_ID)
      .build();

    var result = strategy.resolveOrCreateRoute(info, transitService);

    assertTrue(result.isSuccess());
    assertTrue(result.successValue().isNewRoute());
    var route = result.successValue().route();
    assertEquals(newRouteId, route.getId());
    assertNotNull(route.getAgency());
  }

  @Test
  void failsWhenCannotResolveAgency() {
    var newRouteId = new FeedScopedId(FEED_ID, "new-route");
    // No operator, no replaced route - cannot resolve agency
    var info = TripCreationInfo.builder(TRIP_ID)
      .withRouteId(newRouteId)
      .withMode(TransitMode.BUS)
      .build();

    var result = strategy.resolveOrCreateRoute(info, transitService);

    assertTrue(result.isFailure());
    assertEquals(
      UpdateError.UpdateErrorType.CANNOT_RESOLVE_AGENCY,
      result.failureValue().errorType()
    );
  }

  @Test
  void derivesReplacementRailSubmodeWhenReplacingRailRoute() {
    var railRouteId = new FeedScopedId(FEED_ID, "rail-route");
    var newRouteId = new FeedScopedId(FEED_ID, "replacement-rail");
    var info = TripCreationInfo.builder(TRIP_ID)
      .withRouteId(newRouteId)
      .withOperatorId(OPERATOR_ID)
      .withMode(TransitMode.RAIL)
      .withSubmode("local")
      .withReplacedRouteId(railRouteId)
      .build();

    var result = strategy.resolveOrCreateRoute(info, transitService);

    assertTrue(result.isSuccess());
    assertTrue(result.successValue().isNewRoute());
    assertEquals(
      RailSubmodeEnumeration.REPLACEMENT_RAIL_SERVICE.value(),
      result.successValue().route().getNetexSubmode().name()
    );
  }

  @Test
  void derivesRailReplacementBusSubmodeWhenBusReplacingRail() {
    var railRouteId = new FeedScopedId(FEED_ID, "rail-route");
    var newRouteId = new FeedScopedId(FEED_ID, "replacement-bus");
    var info = TripCreationInfo.builder(TRIP_ID)
      .withRouteId(newRouteId)
      .withOperatorId(OPERATOR_ID)
      .withMode(TransitMode.BUS)
      .withSubmode("localBus")
      .withReplacedRouteId(railRouteId)
      .build();

    var result = strategy.resolveOrCreateRoute(info, transitService);

    assertTrue(result.isSuccess());
    assertTrue(result.successValue().isNewRoute());
    assertEquals(
      BusSubmodeEnumeration.RAIL_REPLACEMENT_BUS.value(),
      result.successValue().route().getNetexSubmode().name()
    );
  }

  @Test
  void usesOriginalSubmodeWhenNotReplacingRailRoute() {
    var newRouteId = new FeedScopedId(FEED_ID, "bus-route");
    // route1 is BUS, not RAIL
    var info = TripCreationInfo.builder(TRIP_ID)
      .withRouteId(newRouteId)
      .withOperatorId(OPERATOR_ID)
      .withMode(TransitMode.BUS)
      .withSubmode("localBus")
      .withReplacedRouteId(ROUTE_ID)
      .build();

    var result = strategy.resolveOrCreateRoute(info, transitService);

    assertTrue(result.isSuccess());
    assertTrue(result.successValue().isNewRoute());
    assertEquals("localBus", result.successValue().route().getNetexSubmode().name());
  }

  @Test
  void usesTripIdAsRouteIdWhenNoRouteId() {
    var info = TripCreationInfo.builder(TRIP_ID)
      .withOperatorId(OPERATOR_ID)
      .withMode(TransitMode.BUS)
      .build();

    var result = strategy.resolveOrCreateRoute(info, transitService);

    assertTrue(result.isSuccess());
    assertTrue(result.successValue().isNewRoute());
    assertEquals(TRIP_ID, result.successValue().route().getId());
  }
}
