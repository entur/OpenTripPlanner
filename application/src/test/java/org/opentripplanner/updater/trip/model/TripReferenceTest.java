package org.opentripplanner.updater.trip.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.transit.model.timetable.Direction;

class TripReferenceTest {

  private static final String FEED_ID = "F";
  private static final FeedScopedId TRIP_ID = new FeedScopedId(FEED_ID, "trip1");
  private static final FeedScopedId ROUTE_ID = new FeedScopedId(FEED_ID, "route1");
  private static final String START_TIME = "08:30:00";
  private static final LocalDate START_DATE = LocalDate.of(2024, 1, 15);

  @Test
  void ofTripIdCreatesMinimalReference() {
    var ref = TripReference.ofTripId(TRIP_ID);

    assertEquals(TRIP_ID, ref.tripId());
    assertNull(ref.routeId());
    assertNull(ref.startTime());
    assertNull(ref.startDate());
    assertNull(ref.direction());
    assertTrue(ref.hasTripId());
  }

  @Test
  void builderCreatesFullReference() {
    var ref = TripReference.builder()
      .withTripId(TRIP_ID)
      .withRouteId(ROUTE_ID)
      .withStartTime(START_TIME)
      .withStartDate(START_DATE)
      .withDirection(Direction.INBOUND)
      .build();

    assertEquals(TRIP_ID, ref.tripId());
    assertEquals(ROUTE_ID, ref.routeId());
    assertEquals(START_TIME, ref.startTime());
    assertEquals(START_DATE, ref.startDate());
    assertEquals(Direction.INBOUND, ref.direction());
  }

  @Test
  void hasTripIdReturnsFalseWhenNull() {
    var ref = TripReference.builder().withRouteId(ROUTE_ID).build();

    assertFalse(ref.hasTripId());
  }

  @Test
  void hasRouteIdReturnsTrueWhenPresent() {
    var ref = TripReference.builder().withTripId(TRIP_ID).withRouteId(ROUTE_ID).build();

    assertTrue(ref.hasRouteId());
  }

  @Test
  void hasStartTimeReturnsTrueWhenPresent() {
    var ref = TripReference.builder().withTripId(TRIP_ID).withStartTime(START_TIME).build();

    assertTrue(ref.hasStartTime());
  }
}
