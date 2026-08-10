package org.opentripplanner.ext.updater.trip.unified.gtfs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.core.model.id.FeedScopedIdForTestFactory;
import org.opentripplanner.ext.updater.trip.unified.model.command.RouteCreationInfo;
import org.opentripplanner.ext.updater.trip.unified.model.command.TripCreationInfo;
import org.opentripplanner.transit.model.TransitTestEnvironment;
import org.opentripplanner.transit.model.TripInput;
import org.opentripplanner.transit.model.basic.TransitMode;
import org.opentripplanner.transit.service.TransitEditorService;

class GtfsRtRouteCreationStrategyTest {

  private static final String FEED_ID = FeedScopedIdForTestFactory.FEED_ID;
  private static final FeedScopedId TRIP_ID = new FeedScopedId(FEED_ID, "new-trip");
  private static final FeedScopedId ROUTE_ID = new FeedScopedId(FEED_ID, "route1");

  private TransitEditorService transitService;

  @BeforeEach
  void setUp() {
    var builder = TransitTestEnvironment.of().addStops("A", "B", "C");

    var route = builder.route("route1");
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
      .build();

    transitService = (TransitEditorService) env.transitService();
  }

  @Test
  void returnsExistingRouteFromTransitService() {
    var strategy = new GtfsRtRouteCreationStrategy(FEED_ID);
    var info = TripCreationInfo.builder(TRIP_ID).withRouteId(ROUTE_ID).build();

    var resolution = strategy.resolveOrCreateRoute(info, null, transitService);

    // A route found in the transit service must not be registered as real-time-added, or it
    // would be listed twice
    assertFalse(resolution.isNewRoute());
    assertEquals(ROUTE_ID, resolution.route().getId());
  }

  @Test
  void createsRouteWithRouteCreationInfo() {
    var newRouteId = new FeedScopedId(FEED_ID, "new-route");
    var routeCreationInfo = new RouteCreationInfo(
      "Test Route",
      TransitMode.BUS,
      null,
      null,
      "http://example.com",
      null,
      3
    );
    var info = TripCreationInfo.builder(TRIP_ID)
      .withRouteId(newRouteId)
      .withRouteCreationInfo(routeCreationInfo)
      .build();

    var strategy = new GtfsRtRouteCreationStrategy(FEED_ID);
    var resolution = strategy.resolveOrCreateRoute(info, null, transitService);

    assertTrue(resolution.isNewRoute());
    var route = resolution.route();
    assertEquals(newRouteId, route.getId());
    assertEquals(TransitMode.BUS, route.getMode());
    assertEquals(3, route.getGtfsType());
    assertEquals("Test Route", route.getLongName().toString());
    assertNotNull(route.getAgency());
  }

  @Test
  void createsFallbackRouteWithRouteId() {
    var newRouteId = new FeedScopedId(FEED_ID, "new-route");
    var info = TripCreationInfo.builder(TRIP_ID).withRouteId(newRouteId).build();

    var strategy = new GtfsRtRouteCreationStrategy(FEED_ID);
    var resolution = strategy.resolveOrCreateRoute(info, null, transitService);

    assertTrue(resolution.isNewRoute());
    var route = resolution.route();
    assertEquals(newRouteId, route.getId());
    assertEquals(TransitMode.BUS, route.getMode());
    assertEquals(3, route.getGtfsType());
    assertNotNull(route.getAgency());
  }

  @Test
  void createsFallbackRouteWithTripIdWhenNoRouteId() {
    var info = TripCreationInfo.builder(TRIP_ID).build();

    var strategy = new GtfsRtRouteCreationStrategy(FEED_ID);
    var resolution = strategy.resolveOrCreateRoute(info, null, transitService);

    assertTrue(resolution.isNewRoute());
    var route = resolution.route();
    assertEquals(TRIP_ID, route.getId());
    assertEquals(TransitMode.BUS, route.getMode());
    assertEquals(3, route.getGtfsType());
  }

  @Test
  void propagatesGtfsTypeFromRouteCreationInfo() {
    var newRouteId = new FeedScopedId(FEED_ID, "tram-route");
    var routeCreationInfo = new RouteCreationInfo(
      "Tram Route",
      TransitMode.TRAM,
      null,
      null,
      null,
      null,
      0
    );
    var info = TripCreationInfo.builder(TRIP_ID)
      .withRouteId(newRouteId)
      .withRouteCreationInfo(routeCreationInfo)
      .build();

    var strategy = new GtfsRtRouteCreationStrategy(FEED_ID);
    var resolution = strategy.resolveOrCreateRoute(info, null, transitService);

    assertTrue(resolution.isNewRoute());
    assertEquals(0, resolution.route().getGtfsType());
    assertEquals(TransitMode.TRAM, resolution.route().getMode());
  }

  @Test
  void fallbackAgencyIsSynthetic() {
    var info = TripCreationInfo.builder(TRIP_ID).build();

    var strategy = new GtfsRtRouteCreationStrategy(FEED_ID);
    var resolution = strategy.resolveOrCreateRoute(info, null, transitService);

    var agency = resolution.route().getAgency();
    assertNotNull(agency);
    assertEquals("autogenerated-gtfs-rt-added-route", agency.getId().getId());
  }
}
