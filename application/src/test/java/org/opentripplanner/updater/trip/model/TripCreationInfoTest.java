package org.opentripplanner.updater.trip.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.opentripplanner.core.model.i18n.NonLocalizedString;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.transit.model.basic.Accessibility;
import org.opentripplanner.transit.model.basic.TransitMode;

class TripCreationInfoTest {

  private static final String FEED_ID = "F";
  private static final FeedScopedId TRIP_ID = new FeedScopedId(FEED_ID, "trip1");
  private static final FeedScopedId ROUTE_ID = new FeedScopedId(FEED_ID, "route1");
  private static final FeedScopedId SERVICE_ID = new FeedScopedId(FEED_ID, "service1");
  private static final FeedScopedId OPERATOR_ID = new FeedScopedId(FEED_ID, "operator1");

  @Test
  void builderCreatesMinimalInfo() {
    var info = TripCreationInfo.builder(TRIP_ID).build();

    assertEquals(TRIP_ID, info.tripId());
    assertNull(info.routeId());
    assertNull(info.routeCreationInfo());
    assertNull(info.serviceId());
    assertNull(info.headsign());
    assertNull(info.shortName());
    assertNull(info.mode());
    assertNull(info.submode());
    assertNull(info.operatorId());
    assertNull(info.wheelchairAccessibility());
    assertTrue(info.replacedTrips().isEmpty());
    assertFalse(info.requiresRouteCreation());
  }

  @Test
  void builderWithAllFields() {
    var headsign = new NonLocalizedString("Downtown");
    var routeCreationInfo = new RouteCreationInfo(
      ROUTE_ID,
      "Route 1",
      TransitMode.BUS,
      "localBus",
      OPERATOR_ID
    );
    var replacedTripId = new FeedScopedId(FEED_ID, "replaced1");

    var info = TripCreationInfo.builder(TRIP_ID)
      .withRouteId(ROUTE_ID)
      .withRouteCreationInfo(routeCreationInfo)
      .withServiceId(SERVICE_ID)
      .withHeadsign(headsign)
      .withShortName("T1")
      .withMode(TransitMode.BUS)
      .withSubmode("localBus")
      .withOperatorId(OPERATOR_ID)
      .withWheelchairAccessibility(Accessibility.POSSIBLE)
      .withReplacedTrips(List.of(replacedTripId))
      .build();

    assertEquals(TRIP_ID, info.tripId());
    assertEquals(ROUTE_ID, info.routeId());
    assertEquals(routeCreationInfo, info.routeCreationInfo());
    assertEquals(SERVICE_ID, info.serviceId());
    assertEquals(headsign, info.headsign());
    assertEquals("T1", info.shortName());
    assertEquals(TransitMode.BUS, info.mode());
    assertEquals("localBus", info.submode());
    assertEquals(OPERATOR_ID, info.operatorId());
    assertEquals(Accessibility.POSSIBLE, info.wheelchairAccessibility());
    assertEquals(1, info.replacedTrips().size());
    assertEquals(replacedTripId, info.replacedTrips().get(0));
    assertTrue(info.requiresRouteCreation());
  }

  @Test
  void requiresRouteCreationWhenRouteCreationInfoPresent() {
    var routeCreationInfo = new RouteCreationInfo(ROUTE_ID, "Route 1", TransitMode.BUS, null, null);

    var info = TripCreationInfo.builder(TRIP_ID).withRouteCreationInfo(routeCreationInfo).build();

    assertTrue(info.requiresRouteCreation());
  }

  @Test
  void doesNotRequireRouteCreationWhenOnlyRouteId() {
    var info = TripCreationInfo.builder(TRIP_ID).withRouteId(ROUTE_ID).build();

    assertFalse(info.requiresRouteCreation());
  }
}
