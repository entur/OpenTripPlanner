package org.opentripplanner.updater.trip.handlers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opentripplanner.core.model.i18n.NonLocalizedString;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.transit.model._data.FeedScopedIdForTestFactory;
import org.opentripplanner.transit.model._data.TransitTestEnvironment;
import org.opentripplanner.transit.model._data.TripInput;
import org.opentripplanner.transit.model.basic.TransitMode;
import org.opentripplanner.transit.model.network.Route;
import org.opentripplanner.transit.service.TransitEditorService;
import org.opentripplanner.updater.trip.model.RouteCreationInfo;
import org.opentripplanner.updater.trip.model.TripCreationInfo;

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
    var strategy = new GtfsRtRouteCreationStrategy(FEED_ID, null);
    var info = TripCreationInfo.builder(TRIP_ID).withRouteId(ROUTE_ID).build();

    var result = strategy.resolveOrCreateRoute(info, transitService);

    assertTrue(result.isSuccess());
    // Route found via transit service (not cache) is marked as new to ensure
    // re-registration in FULL update mode snapshots
    assertTrue(result.successValue().isNewRoute());
    assertEquals(ROUTE_ID, result.successValue().route().getId());
  }

  @Test
  void returnsRouteFromCacheFirst() {
    var cachedRouteId = new FeedScopedId(FEED_ID, "cached-route");
    Map<FeedScopedId, Route> cache = new HashMap<>();
    var cachedRoute = Route.of(cachedRouteId)
      .withAgency(transitService.listAgencies().iterator().next())
      .withMode(TransitMode.TRAM)
      .withLongName(new NonLocalizedString("Cached Tram"))
      .build();
    cache.put(cachedRouteId, cachedRoute);

    var strategy = new GtfsRtRouteCreationStrategy(FEED_ID, cache::get);
    var info = TripCreationInfo.builder(TRIP_ID).withRouteId(cachedRouteId).build();

    var result = strategy.resolveOrCreateRoute(info, transitService);

    assertTrue(result.isSuccess());
    // GTFS-RT always marks isNewRoute=true for FULL_DATASET re-registration
    assertTrue(result.successValue().isNewRoute());
    assertEquals(cachedRouteId, result.successValue().route().getId());
    assertEquals(TransitMode.TRAM, result.successValue().route().getMode());
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

    var strategy = new GtfsRtRouteCreationStrategy(FEED_ID, null);
    var result = strategy.resolveOrCreateRoute(info, transitService);

    assertTrue(result.isSuccess());
    assertTrue(result.successValue().isNewRoute());
    var route = result.successValue().route();
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

    var strategy = new GtfsRtRouteCreationStrategy(FEED_ID, null);
    var result = strategy.resolveOrCreateRoute(info, transitService);

    assertTrue(result.isSuccess());
    assertTrue(result.successValue().isNewRoute());
    var route = result.successValue().route();
    assertEquals(newRouteId, route.getId());
    assertEquals(TransitMode.BUS, route.getMode());
    assertEquals(3, route.getGtfsType());
    assertNotNull(route.getAgency());
  }

  @Test
  void createsFallbackRouteWithTripIdWhenNoRouteId() {
    var info = TripCreationInfo.builder(TRIP_ID).build();

    var strategy = new GtfsRtRouteCreationStrategy(FEED_ID, null);
    var result = strategy.resolveOrCreateRoute(info, transitService);

    assertTrue(result.isSuccess());
    assertTrue(result.successValue().isNewRoute());
    var route = result.successValue().route();
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

    var strategy = new GtfsRtRouteCreationStrategy(FEED_ID, null);
    var result = strategy.resolveOrCreateRoute(info, transitService);

    assertTrue(result.isSuccess());
    assertTrue(result.successValue().isNewRoute());
    assertEquals(0, result.successValue().route().getGtfsType());
    assertEquals(TransitMode.TRAM, result.successValue().route().getMode());
  }

  @Test
  void fallbackAgencyIsSynthetic() {
    var info = TripCreationInfo.builder(TRIP_ID).build();

    var strategy = new GtfsRtRouteCreationStrategy(FEED_ID, null);
    var result = strategy.resolveOrCreateRoute(info, transitService);

    assertTrue(result.isSuccess());
    var agency = result.successValue().route().getAgency();
    assertNotNull(agency);
    assertEquals("autogenerated-gtfs-rt-added-route", agency.getId().getId());
  }
}
