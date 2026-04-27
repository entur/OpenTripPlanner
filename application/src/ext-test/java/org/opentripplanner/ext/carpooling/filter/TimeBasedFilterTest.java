package org.opentripplanner.ext.carpooling.filter;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opentripplanner.ext.carpooling.CarpoolTestCoordinates.OSLO_CENTER;
import static org.opentripplanner.ext.carpooling.CarpoolTestCoordinates.OSLO_EAST;
import static org.opentripplanner.ext.carpooling.CarpoolTestCoordinates.OSLO_NORTH;
import static org.opentripplanner.ext.carpooling.CarpoolTestCoordinates.OSLO_NORTHEAST;
import static org.opentripplanner.ext.carpooling.CarpoolTripTestData.createSimpleTripWithTime;
import static org.opentripplanner.ext.carpooling.CarpoolTripTestData.createSimpleTripWithTimes;

import java.time.Duration;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;

class TimeBasedFilterTest {

  private final TimeBasedFilter filter = new TimeBasedFilter();
  private static final boolean ARRIVE_BY = true;
  private static final boolean DEPART_AFTER = false;

  // ---------------------------------------------------------------------------
  // arrive-by: pre-filter uses trip.startTime <= T (necessary condition only)
  // Tight enforcement of itinerary.endTime <= T is done by ArriveByFilter.
  // ---------------------------------------------------------------------------

  @Test
  void accepts_arriveByPassengerRequestWithinTimeWindow_returnsTrue() {
    var startTime = ZonedDateTime.parse("2024-01-15T10:00:00+01:00");
    var endTime = ZonedDateTime.parse("2024-01-15T11:15:00+01:00");
    var trip = createSimpleTripWithTimes(OSLO_CENTER, OSLO_NORTH, startTime, endTime);

    // Passenger requests arrive by at 11:30 (15 minutes after trip arrival)
    var passengerRequestTime = endTime.plusMinutes(15).toInstant();
    var request = new CarpoolingRequestBuilder()
      .withArriveBy(ARRIVE_BY)
      .withPassengerPickup(OSLO_EAST)
      .withPassengerDropoff(OSLO_NORTHEAST)
      .withRequestedDateTime(passengerRequestTime)
      .build();

    assertTrue(filter.accepts(trip, request, Duration.ofMinutes(30)));
  }

  @Test
  void accepts_arriveByPassengerRequestExactlyAtTripArrival_returnsTrue() {
    var startTime = ZonedDateTime.parse("2024-01-15T10:00:00+01:00");
    var endTime = ZonedDateTime.parse("2024-01-15T11:15:00+01:00");
    var trip = createSimpleTripWithTimes(OSLO_CENTER, OSLO_NORTH, startTime, endTime);

    var passengerRequestTime = endTime.toInstant();
    var request = new CarpoolingRequestBuilder()
      .withArriveBy(ARRIVE_BY)
      .withPassengerPickup(OSLO_EAST)
      .withPassengerDropoff(OSLO_NORTHEAST)
      .withRequestedDateTime(passengerRequestTime)
      .build();

    assertTrue(filter.accepts(trip, request, Duration.ofMinutes(30)));
  }

  @Test
  void accepts_arriveByTripArrivesBeforeDeadline_returnsTrue() {
    var startTime = ZonedDateTime.parse("2024-01-15T10:00:00+01:00");
    var endTime = ZonedDateTime.parse("2024-01-15T11:15:00+01:00");
    var trip = createSimpleTripWithTimes(OSLO_CENTER, OSLO_NORTH, startTime, endTime);

    var passengerRequestTime = endTime.plusMinutes(30).toInstant();
    var request = new CarpoolingRequestBuilder()
      .withArriveBy(ARRIVE_BY)
      .withPassengerPickup(OSLO_EAST)
      .withPassengerDropoff(OSLO_NORTHEAST)
      .withRequestedDateTime(passengerRequestTime)
      .build();

    assertTrue(filter.accepts(trip, request, Duration.ofMinutes(30)));
  }

  @Test
  void accepts_arriveByTripArrivesVeryEarlyBeforeDeadline_returnsTrue() {
    var startTime = ZonedDateTime.parse("2024-01-15T10:00:00+01:00");
    var endTime = ZonedDateTime.parse("2024-01-15T11:15:00+01:00");
    var trip = createSimpleTripWithTimes(OSLO_CENTER, OSLO_NORTH, startTime, endTime);

    // Passenger requests arrive-by 15:00 — no lower bound for arrive-by.
    var passengerRequestTime = endTime.plusHours(3).plusMinutes(45).toInstant();
    var request = new CarpoolingRequestBuilder()
      .withArriveBy(ARRIVE_BY)
      .withPassengerPickup(OSLO_EAST)
      .withPassengerDropoff(OSLO_NORTHEAST)
      .withRequestedDateTime(passengerRequestTime)
      .build();

    assertTrue(filter.accepts(trip, request, Duration.ofMinutes(30)));
  }

  @Test
  void accepts_arriveByTripStartsBeforeDeadline_returnsTrueEvenWhenDriverEndIsLate() {
    // The driver continues past the passenger's deadline — that is fine because the passenger
    // is dropped off mid-route. The tight check is ArriveByFilter on the itinerary end time.
    var startTime = ZonedDateTime.parse("2024-01-15T10:00:00+01:00");
    var endTime = ZonedDateTime.parse("2024-01-15T11:15:00+01:00");
    var trip = createSimpleTripWithTimes(OSLO_CENTER, OSLO_NORTH, startTime, endTime);

    // Passenger must arrive by 11:00 — trip ends at 11:15, but passenger's dropoff may be earlier.
    var passengerRequestTime = endTime.minusMinutes(15).toInstant();
    var request = new CarpoolingRequestBuilder()
      .withArriveBy(ARRIVE_BY)
      .withPassengerPickup(OSLO_EAST)
      .withPassengerDropoff(OSLO_NORTHEAST)
      .withRequestedDateTime(passengerRequestTime)
      .build();

    assertTrue(filter.accepts(trip, request, Duration.ofMinutes(30)));
  }

  @Test
  void accepts_arriveByTripStartsBeforeDeadlineAtBoundary_returnsTrueEvenWhenDriverEndIsLate() {
    var startTime = ZonedDateTime.parse("2024-01-15T10:00:00+01:00");
    var endTime = ZonedDateTime.parse("2024-01-15T11:15:00+01:00");
    var trip = createSimpleTripWithTimes(OSLO_CENTER, OSLO_NORTH, startTime, endTime);

    // Passenger must arrive by 10:45 — trip ends 30 minutes late, but passenger may be dropped
    // off before that.
    var passengerRequestTime = endTime.minusMinutes(30).toInstant();
    var request = new CarpoolingRequestBuilder()
      .withArriveBy(ARRIVE_BY)
      .withPassengerPickup(OSLO_EAST)
      .withPassengerDropoff(OSLO_NORTHEAST)
      .withRequestedDateTime(passengerRequestTime)
      .build();

    assertTrue(filter.accepts(trip, request, Duration.ofMinutes(30)));
  }

  @Test
  void accepts_arriveByTripStartsAfterDeadline_returnsFalse() {
    // The driver hasn't even started yet by the time the passenger needs to arrive — impossible.
    var startTime = ZonedDateTime.parse("2024-01-15T12:00:00+01:00");
    var endTime = ZonedDateTime.parse("2024-01-15T13:00:00+01:00");
    var trip = createSimpleTripWithTimes(OSLO_CENTER, OSLO_NORTH, startTime, endTime);

    // Passenger needs to arrive by 11:00, but driver doesn't start until 12:00.
    var passengerRequestTime = ZonedDateTime.parse("2024-01-15T11:00:00+01:00").toInstant();
    var request = new CarpoolingRequestBuilder()
      .withArriveBy(ARRIVE_BY)
      .withPassengerPickup(OSLO_EAST)
      .withPassengerDropoff(OSLO_NORTHEAST)
      .withRequestedDateTime(passengerRequestTime)
      .build();

    assertFalse(filter.accepts(trip, request, Duration.ofMinutes(30)));
  }

  // ---------------------------------------------------------------------------
  // depart-after: pre-filter uses endTime >= T && startTime <= T+window
  // Tight enforcement of itinerary.startTime >= T is done by DepartAfterFilter.
  // ---------------------------------------------------------------------------

  @Test
  void accepts_departAfterTripUnderwayAtRequestedTime_returnsTrue() {
    // Trip started 15 minutes ago but is still running — passenger can board mid-route.
    var tripDepartureTime = ZonedDateTime.parse("2024-01-15T10:00:00+01:00");
    var trip = createSimpleTripWithTime(OSLO_CENTER, OSLO_NORTH, tripDepartureTime);

    var passengerRequestTime = tripDepartureTime.plusMinutes(15).toInstant();
    var request = new CarpoolingRequestBuilder()
      .withArriveBy(DEPART_AFTER)
      .withPassengerPickup(OSLO_EAST)
      .withPassengerDropoff(OSLO_NORTHEAST)
      .withRequestedDateTime(passengerRequestTime)
      .build();

    assertTrue(filter.accepts(trip, request, Duration.ofMinutes(30)));
  }

  @Test
  void accepts_departAfterPassengerRequestExactlyAtTripDeparture_returnsTrue() {
    var tripDepartureTime = ZonedDateTime.parse("2024-01-15T10:00:00+01:00");
    var trip = createSimpleTripWithTime(OSLO_CENTER, OSLO_NORTH, tripDepartureTime);

    var passengerRequestTime = tripDepartureTime.toInstant();
    var request = new CarpoolingRequestBuilder()
      .withArriveBy(DEPART_AFTER)
      .withPassengerPickup(OSLO_EAST)
      .withPassengerDropoff(OSLO_NORTHEAST)
      .withRequestedDateTime(passengerRequestTime)
      .build();

    assertTrue(filter.accepts(trip, request, Duration.ofMinutes(30)));
  }

  @Test
  void accepts_departAfterTripUnderwayAtWindowBoundary_returnsTrue() {
    // Trip started 30 minutes ago but is still running — still a candidate for mid-route pickup.
    var tripDepartureTime = ZonedDateTime.parse("2024-01-15T10:00:00+01:00");
    var trip = createSimpleTripWithTime(OSLO_CENTER, OSLO_NORTH, tripDepartureTime);

    var passengerRequestTime = tripDepartureTime.plusMinutes(30).toInstant();
    var request = new CarpoolingRequestBuilder()
      .withArriveBy(DEPART_AFTER)
      .withPassengerPickup(OSLO_EAST)
      .withPassengerDropoff(OSLO_NORTHEAST)
      .withRequestedDateTime(passengerRequestTime)
      .build();

    assertTrue(filter.accepts(trip, request, Duration.ofMinutes(30)));
  }

  @Test
  void accepts_departAfterPassengerRequestBeforeTripDepartureWithinWindow_returnsTrue() {
    var tripDepartureTime = ZonedDateTime.parse("2024-01-15T10:00:00+01:00");
    var trip = createSimpleTripWithTime(OSLO_CENTER, OSLO_NORTH, tripDepartureTime);

    // Passenger requests 20 minutes before trip departs (within 30-min window)
    var passengerRequestTime = tripDepartureTime.minusMinutes(20).toInstant();
    var request = new CarpoolingRequestBuilder()
      .withArriveBy(DEPART_AFTER)
      .withPassengerPickup(OSLO_EAST)
      .withPassengerDropoff(OSLO_NORTHEAST)
      .withRequestedDateTime(passengerRequestTime)
      .build();

    assertTrue(filter.accepts(trip, request, Duration.ofMinutes(30)));
  }

  @Test
  void accepts_departAfterTripUnderwayLongBeforeRequestedTime_returnsTrue() {
    // Trip started 45 minutes before the requested time but is still running (ends at T+15min).
    // Mid-route pickup after the requested departure time is still possible.
    var tripDepartureTime = ZonedDateTime.parse("2024-01-15T10:00:00+01:00");
    var trip = createSimpleTripWithTime(OSLO_CENTER, OSLO_NORTH, tripDepartureTime);

    // endTime = 11:00; passengerRequestTime = 10:45; endTime >= T => accepted
    var passengerRequestTime = tripDepartureTime.plusMinutes(45).toInstant();
    var request = new CarpoolingRequestBuilder()
      .withArriveBy(DEPART_AFTER)
      .withPassengerPickup(OSLO_EAST)
      .withPassengerDropoff(OSLO_NORTHEAST)
      .withRequestedDateTime(passengerRequestTime)
      .build();

    assertTrue(filter.accepts(trip, request, Duration.ofMinutes(30)));
  }

  @Test
  void accepts_departAfterPassengerRequestTooFarInPast_returnsFalse() {
    // Trip starts 45 minutes after the passenger's request — outside the search window.
    var tripDepartureTime = ZonedDateTime.parse("2024-01-15T10:00:00+01:00");
    var trip = createSimpleTripWithTime(OSLO_CENTER, OSLO_NORTH, tripDepartureTime);

    // Passenger requests at 09:15; trip starts at 10:00 (45 min later, outside 30-min window)
    var passengerRequestTime = tripDepartureTime.minusMinutes(45).toInstant();
    var request = new CarpoolingRequestBuilder()
      .withArriveBy(DEPART_AFTER)
      .withPassengerPickup(OSLO_EAST)
      .withPassengerDropoff(OSLO_NORTHEAST)
      .withRequestedDateTime(passengerRequestTime)
      .build();

    assertFalse(filter.accepts(trip, request, Duration.ofMinutes(30)));
  }

  @Test
  void accepts_departAfterTripFinishedBeforeRequestedTime_returnsFalse() {
    // Trip has already ended before the passenger wants to depart — no pickup possible.
    var tripDepartureTime = ZonedDateTime.parse("2024-01-15T10:00:00+01:00");
    var trip = createSimpleTripWithTime(OSLO_CENTER, OSLO_NORTH, tripDepartureTime);

    // endTime = 11:00; passengerRequestTime = 12:00 — trip is over.
    var passengerRequestTime = tripDepartureTime.plusHours(2).toInstant();
    var request = new CarpoolingRequestBuilder()
      .withArriveBy(DEPART_AFTER)
      .withPassengerPickup(OSLO_EAST)
      .withPassengerDropoff(OSLO_NORTHEAST)
      .withRequestedDateTime(passengerRequestTime)
      .build();

    assertFalse(filter.accepts(trip, request, Duration.ofMinutes(30)));
  }

  @Test
  void accepts_withoutTimeParameter_alwaysReturnsTrue() {
    var tripDepartureTime = ZonedDateTime.parse("2024-01-15T10:00:00+01:00");
    var trip = createSimpleTripWithTime(OSLO_CENTER, OSLO_NORTH, tripDepartureTime);
    var request = new CarpoolingRequestBuilder()
      .withArriveBy(DEPART_AFTER)
      .withPassengerPickup(OSLO_EAST)
      .withPassengerDropoff(OSLO_NORTHEAST)
      .build();

    assertTrue(filter.accepts(trip, request, null));
  }

  // ---------------------------------------------------------------------------
  // acceptsAccessEgress — same logic as accepts
  // ---------------------------------------------------------------------------

  @Test
  void acceptsAccessEgress_departAfterTripDepartsWithinWindow_returnsTrue() {
    var tripDepartureTime = ZonedDateTime.parse("2024-01-15T10:00:00+01:00");
    var trip = createSimpleTripWithTime(OSLO_CENTER, OSLO_NORTH, tripDepartureTime);

    var passengerRequestTime = tripDepartureTime.minusMinutes(15).toInstant();
    var request = new CarpoolingRequestBuilder()
      .withArriveBy(DEPART_AFTER)
      .withPassengerPickup(OSLO_EAST)
      .withPassengerDropoff(OSLO_NORTHEAST)
      .withRequestedDateTime(passengerRequestTime)
      .build();

    assertTrue(filter.acceptsAccessEgress(trip, request, Duration.ofMinutes(30)));
  }

  @Test
  void acceptsAccessEgress_departAfterTripUnderwayAtRequestedTime_returnsTrue() {
    // Trip started 15 minutes ago but is still running — mid-route pickup possible.
    var tripDepartureTime = ZonedDateTime.parse("2024-01-15T10:00:00+01:00");
    var trip = createSimpleTripWithTime(OSLO_CENTER, OSLO_NORTH, tripDepartureTime);

    var passengerRequestTime = tripDepartureTime.plusMinutes(15).toInstant();
    var request = new CarpoolingRequestBuilder()
      .withArriveBy(DEPART_AFTER)
      .withPassengerPickup(OSLO_EAST)
      .withPassengerDropoff(OSLO_NORTHEAST)
      .withRequestedDateTime(passengerRequestTime)
      .build();

    assertTrue(filter.acceptsAccessEgress(trip, request, Duration.ofMinutes(30)));
  }

  @Test
  void acceptsAccessEgress_departAfterTripDepartsTooFarInFuture_returnsFalse() {
    var tripDepartureTime = ZonedDateTime.parse("2024-01-15T10:00:00+01:00");
    var trip = createSimpleTripWithTime(OSLO_CENTER, OSLO_NORTH, tripDepartureTime);

    // Passenger requests at 08:45 — trip starts 75 minutes later, outside 30-min window.
    var passengerRequestTime = tripDepartureTime.minusMinutes(75).toInstant();
    var request = new CarpoolingRequestBuilder()
      .withArriveBy(DEPART_AFTER)
      .withPassengerPickup(OSLO_EAST)
      .withPassengerDropoff(OSLO_NORTHEAST)
      .withRequestedDateTime(passengerRequestTime)
      .build();

    assertFalse(filter.acceptsAccessEgress(trip, request, Duration.ofMinutes(30)));
  }

  @Test
  void acceptsAccessEgress_arriveByTripStartsBeforeDeadline_returnsTrue() {
    var startTime = ZonedDateTime.parse("2024-01-15T10:00:00+01:00");
    var endTime = ZonedDateTime.parse("2024-01-15T11:15:00+01:00");
    var trip = createSimpleTripWithTimes(OSLO_CENTER, OSLO_NORTH, startTime, endTime);

    // Trip ends after deadline but started before — passenger may be dropped off before the end.
    var passengerRequestTime = endTime.minusMinutes(15).toInstant();
    var request = new CarpoolingRequestBuilder()
      .withArriveBy(ARRIVE_BY)
      .withPassengerPickup(OSLO_EAST)
      .withPassengerDropoff(OSLO_NORTHEAST)
      .withRequestedDateTime(passengerRequestTime)
      .build();

    assertTrue(filter.acceptsAccessEgress(trip, request, Duration.ofMinutes(30)));
  }

  @Test
  void acceptsAccessEgress_arriveByTripArrivesWithinWindow_returnsTrue() {
    var startTime = ZonedDateTime.parse("2024-01-15T10:00:00+01:00");
    var endTime = ZonedDateTime.parse("2024-01-15T11:15:00+01:00");
    var trip = createSimpleTripWithTimes(OSLO_CENTER, OSLO_NORTH, startTime, endTime);

    var passengerRequestTime = endTime.plusMinutes(15).toInstant();
    var request = new CarpoolingRequestBuilder()
      .withArriveBy(ARRIVE_BY)
      .withPassengerPickup(OSLO_EAST)
      .withPassengerDropoff(OSLO_NORTHEAST)
      .withRequestedDateTime(passengerRequestTime)
      .build();

    assertTrue(filter.acceptsAccessEgress(trip, request, Duration.ofMinutes(30)));
  }

  @Test
  void acceptsAccessEgress_arriveByTripArrivesWellBeforeDeadline_returnsTrue() {
    var startTime = ZonedDateTime.parse("2024-01-15T10:00:00+01:00");
    var endTime = ZonedDateTime.parse("2024-01-15T11:15:00+01:00");
    var trip = createSimpleTripWithTimes(OSLO_CENTER, OSLO_NORTH, startTime, endTime);

    var passengerRequestTime = endTime.plusMinutes(105).toInstant();
    var request = new CarpoolingRequestBuilder()
      .withArriveBy(ARRIVE_BY)
      .withPassengerPickup(OSLO_EAST)
      .withPassengerDropoff(OSLO_NORTHEAST)
      .withRequestedDateTime(passengerRequestTime)
      .build();

    assertTrue(filter.acceptsAccessEgress(trip, request, Duration.ofMinutes(30)));
  }

  @Test
  void acceptsAccessEgress_withoutTimeParameter_alwaysReturnsTrue() {
    var tripDepartureTime = ZonedDateTime.parse("2024-01-15T10:00:00+01:00");
    var trip = createSimpleTripWithTime(OSLO_CENTER, OSLO_NORTH, tripDepartureTime);
    var request = new CarpoolingRequestBuilder()
      .withArriveBy(DEPART_AFTER)
      .withPassengerPickup(OSLO_EAST)
      .withPassengerDropoff(OSLO_NORTHEAST)
      .build();

    assertTrue(filter.acceptsAccessEgress(trip, request, Duration.ofMinutes(30)));
  }
}
