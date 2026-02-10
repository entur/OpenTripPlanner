package org.opentripplanner.routing.api.request;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.opentripplanner.core.model.id.FeedScopedId;

class TripLocationTest {

  private static final FeedScopedId TRIP_ID = new FeedScopedId("F", "trip1");
  private static final LocalDate SERVICE_DATE = LocalDate.of(2024, 11, 1);
  private static final FeedScopedId STOP_ID = new FeedScopedId("F", "stop1");
  private static final TripOnDateReference TRIP_REF = TripOnDateReference.ofTripIdAndServiceDate(
    TRIP_ID,
    SERVICE_DATE
  );

  @Test
  void createWithoutScheduledDepartureTime() {
    var tripLocation = new TripLocation(TRIP_REF, STOP_ID, null);
    assertEquals(TRIP_REF, tripLocation.tripOnDateReference());
    assertEquals(STOP_ID, tripLocation.stopId());
    assertNull(tripLocation.scheduledDepartureTime());
  }

  @Test
  void createWithScheduledDepartureTime() {
    var depTime = Instant.parse("2024-11-01T10:30:00Z");
    var tripLocation = new TripLocation(TRIP_REF, STOP_ID, depTime);
    assertEquals(depTime, tripLocation.scheduledDepartureTime());
  }

  @Test
  void throwsWhenTripReferenceIsNull() {
    assertThrows(IllegalArgumentException.class, () -> new TripLocation(null, STOP_ID, null));
  }

  @Test
  void throwsWhenStopIdIsNull() {
    assertThrows(IllegalArgumentException.class, () -> new TripLocation(TRIP_REF, null, null));
  }
}
