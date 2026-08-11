package org.opentripplanner.ext.updater.trip.unified.model.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.transit.model.basic.TransitMode;

class TripCreationInfoTest {

  private static final String FEED_ID = "F";
  private static final FeedScopedId TRIP_ID = new FeedScopedId(FEED_ID, "trip1");
  private static final FeedScopedId ROUTE_ID = new FeedScopedId(FEED_ID, "route1");
  private static final FeedScopedId TRIP_ON_SERVICE_DATE_ID = new FeedScopedId(
    FEED_ID,
    "RUT:DatedServiceJourney:1234"
  );
  private static final FeedScopedId OPERATOR_ID = new FeedScopedId(FEED_ID, "operator1");

  @Test
  void builderCreatesMinimalInfo() {
    var info = TripCreationInfo.builder(TRIP_ID).build();

    assertEquals(TRIP_ID, info.tripId());
    assertNull(info.routeId());
    assertNull(info.routeCreationInfo());
    assertNull(info.tripOnServiceDateId());
    assertNull(info.tripShortName());
    assertNull(info.publishedLineName());
    assertNull(info.mode());
    assertNull(info.operatorId());
    assertTrue(info.replacedTrips().isEmpty());
  }

  @Test
  void builderWithAllFields() {
    var routeCreationInfo = new RouteCreationInfo(
      "Route 1",
      TransitMode.BUS,
      "localBus",
      OPERATOR_ID
    );
    var replacedTrip = new ReplacedTripReference.DatedTripRef(
      new FeedScopedId(FEED_ID, "replaced1")
    );

    var info = TripCreationInfo.builder(TRIP_ID)
      .withRouteId(ROUTE_ID)
      .withRouteCreationInfo(routeCreationInfo)
      .withTripOnServiceDateId(TRIP_ON_SERVICE_DATE_ID)
      .withTripShortName("T1")
      .withPublishedLineName("L1")
      .withMode(TransitMode.BUS)
      .withOperatorId(OPERATOR_ID)
      .withReplacedTrips(List.of(replacedTrip))
      .build();

    assertEquals(TRIP_ID, info.tripId());
    assertEquals(ROUTE_ID, info.routeId());
    assertEquals(routeCreationInfo, info.routeCreationInfo());
    assertEquals(TRIP_ON_SERVICE_DATE_ID, info.tripOnServiceDateId());
    assertEquals("T1", info.tripShortName());
    assertEquals("L1", info.publishedLineName());
    assertEquals(TransitMode.BUS, info.mode());
    assertEquals(OPERATOR_ID, info.operatorId());
    assertEquals(1, info.replacedTrips().size());
    assertEquals(replacedTrip, info.replacedTrips().get(0));
  }
}
