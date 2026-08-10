package org.opentripplanner.ext.updater.trip.unified.siri;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.core.model.id.FeedScopedIdForTestFactory;
import org.opentripplanner.ext.updater.trip.unified.factory.RouteCreationStrategy;
import org.opentripplanner.ext.updater.trip.unified.model.command.TripCreationInfo;
import org.opentripplanner.transit.model.TransitTestEnvironment;
import org.opentripplanner.transit.model.TripInput;
import org.opentripplanner.transit.model.basic.SubMode;
import org.opentripplanner.transit.model.basic.TransitMode;
import org.opentripplanner.transit.service.TransitEditorService;
import org.opentripplanner.updater.spi.UpdateErrorType;
import org.opentripplanner.updater.spi.UpdateException;
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
    strategy = new SiriRouteCreationStrategy();
  }

  /**
   * Resolve the route the way {@link org.opentripplanner.ext.updater.trip.unified.factory.TripAdditionFactory}
   * does: the operator is resolved against the transit model before the route, and handed to the
   * strategy, so that the created trip and its route are stamped with the same one.
   */
  private RouteCreationStrategy.RouteResolution resolveRoute(TripCreationInfo info) {
    var operator = info.operatorId() != null ? transitService.getOperator(info.operatorId()) : null;
    return strategy.resolveOrCreateRoute(info, operator, transitService);
  }

  @Test
  void returnsExistingRouteWhenFound() {
    var info = TripCreationInfo.builder(TRIP_ID).withRouteId(ROUTE_ID).build();

    var resolution = resolveRoute(info);

    assertFalse(resolution.isNewRoute());
    assertEquals(ROUTE_ID, resolution.route().getId());
  }

  @Test
  void createsRouteWithOperatorAgencyResolution() {
    var newRouteId = new FeedScopedId(FEED_ID, "new-route");
    var info = TripCreationInfo.builder(TRIP_ID)
      .withRouteId(newRouteId)
      .withOperatorId(OPERATOR_ID)
      .withMode(TransitMode.BUS)
      .withPublishedLineName("B1")
      .build();

    var resolution = resolveRoute(info);

    assertTrue(resolution.isNewRoute());
    var route = resolution.route();
    assertEquals(newRouteId, route.getId());
    assertEquals(TransitMode.BUS, route.getMode());
    assertEquals("B1", route.getShortName());
    assertNotNull(route.getAgency());
    assertNotNull(route.getOperator());
    assertEquals(OPERATOR_ID, route.getOperator().getId());
  }

  @Test
  void namesTheRouteAfterThePublishedLineNameOnly() {
    var newRouteId = new FeedScopedId(FEED_ID, "new-route");
    var info = TripCreationInfo.builder(TRIP_ID)
      .withRouteId(newRouteId)
      .withOperatorId(OPERATOR_ID)
      .withMode(TransitMode.BUS)
      .withTripShortName("T1")
      .build();

    var route = resolveRoute(info).route();

    assertNull(route.getShortName(), "The short name of a trip does not name its line");
    assertEquals(newRouteId.getId(), route.getLongName().toString());
  }

  @Test
  void createsRouteWithReplacedRouteAgencyFallback() {
    var newRouteId = new FeedScopedId(FEED_ID, "new-route");
    var info = TripCreationInfo.builder(TRIP_ID)
      .withRouteId(newRouteId)
      .withMode(TransitMode.BUS)
      .withReplacedRouteId(ROUTE_ID)
      .build();

    var resolution = resolveRoute(info);

    assertTrue(resolution.isNewRoute());
    var route = resolution.route();
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

    var ex = assertThrows(UpdateException.class, () -> resolveRoute(info));
    assertEquals(UpdateErrorType.CANNOT_RESOLVE_AGENCY, ex.errorType());
  }

  @Test
  void derivesReplacementRailSubmodeWhenReplacingRailRoute() {
    var railRouteId = new FeedScopedId(FEED_ID, "rail-route");
    var newRouteId = new FeedScopedId(FEED_ID, "replacement-rail");
    var info = TripCreationInfo.builder(TRIP_ID)
      .withRouteId(newRouteId)
      .withOperatorId(OPERATOR_ID)
      .withMode(TransitMode.RAIL)
      .withReplacedRouteId(railRouteId)
      .build();

    var resolution = resolveRoute(info);

    assertTrue(resolution.isNewRoute());
    assertEquals(
      RailSubmodeEnumeration.REPLACEMENT_RAIL_SERVICE.value(),
      resolution.route().getNetexSubmode().name()
    );
    assertEquals(
      RailSubmodeEnumeration.REPLACEMENT_RAIL_SERVICE.value(),
      resolution.netexSubmode(),
      "The created trip is classified the same way as the route created for it"
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
      .withReplacedRouteId(railRouteId)
      .build();

    var resolution = resolveRoute(info);

    assertTrue(resolution.isNewRoute());
    assertEquals(
      BusSubmodeEnumeration.RAIL_REPLACEMENT_BUS.value(),
      resolution.route().getNetexSubmode().name()
    );
  }

  @Test
  void derivesNoSubmodeWhenNotReplacingARailRoute() {
    var newRouteId = new FeedScopedId(FEED_ID, "bus-route");
    // route1 is BUS, not RAIL
    var info = TripCreationInfo.builder(TRIP_ID)
      .withRouteId(newRouteId)
      .withOperatorId(OPERATOR_ID)
      .withMode(TransitMode.BUS)
      .withReplacedRouteId(ROUTE_ID)
      .build();

    var resolution = resolveRoute(info);

    assertTrue(resolution.isNewRoute());
    assertNull(resolution.netexSubmode(), "Only a rail replacement classifies an extra journey");
    assertEquals(SubMode.UNKNOWN, resolution.route().getNetexSubmode());
  }

  /**
   * A journey naming no ExternalLineRef is classified against the line it runs on, as legacy
   * {@code AddedTripBuilder} does - so a journey on an existing rail line runs a replacement rail
   * service even though it replaces nothing explicitly.
   */
  @Test
  void classifiesAgainstItsOwnLineWhenNothingIsReplaced() {
    var railRouteId = new FeedScopedId(FEED_ID, "rail-route");
    var info = TripCreationInfo.builder(TRIP_ID)
      .withRouteId(railRouteId)
      .withOperatorId(OPERATOR_ID)
      .withMode(TransitMode.RAIL)
      .build();

    var resolution = resolveRoute(info);

    assertFalse(resolution.isNewRoute());
    assertEquals(
      RailSubmodeEnumeration.REPLACEMENT_RAIL_SERVICE.value(),
      resolution.netexSubmode()
    );
  }

  /**
   * The SIRI parser rejects an extra journey without LineRef, so a SIRI trip creation always
   * names its route. The strategy never makes a route id up - in particular not one keyed by the
   * trip id, which would publish a phantom route named after the ServiceJourney.
   */
  @Test
  void rejectsTripCreationWithoutRouteId() {
    var info = TripCreationInfo.builder(TRIP_ID)
      .withOperatorId(OPERATOR_ID)
      .withMode(TransitMode.BUS)
      .build();

    assertThrows(NullPointerException.class, () -> resolveRoute(info));
  }
}
