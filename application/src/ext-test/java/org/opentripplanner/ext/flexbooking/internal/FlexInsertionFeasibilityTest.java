package org.opentripplanner.ext.flexbooking.internal;

import static com.google.common.truth.Truth.assertThat;
import static org.opentripplanner.core.model.id.FeedScopedIdForTestFactory.id;
import static org.opentripplanner.model.FlexStopTimesFactory.area;
import static org.opentripplanner.transit.model._data.TimetableRepositoryForTest.trip;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.opentripplanner.ext.flex.trip.UnscheduledTrip;
import org.opentripplanner.transit.model.timetable.booking.RoutingBookingInfo;
import org.opentripplanner.utils.time.ServiceDateUtils;

class FlexInsertionFeasibilityTest {

  private static final ZoneId ZONE = ZoneId.of("Europe/Oslo");
  private static final LocalDate SERVICE_DATE = LocalDate.of(2026, 6, 5);
  private static final ZonedDateTime START_OF_SERVICE = ServiceDateUtils.asStartOfService(
    SERVICE_DATE,
    ZONE
  );
  /** The trip's stop time windows are 10:00–14:00 at both the board and the alight position. */
  private static final UnscheduledTrip TRIP = UnscheduledTrip.of(id("flex-1"))
    .withTrip(trip("flex-1").build())
    .withStopTimes(List.of(area("10:00", "14:00"), area("10:00", "14:00")))
    .build();
  private static final int BOARD_POS = 0;
  private static final int ALIGHT_POS = 1;

  private static final ZonedDateTime TOUR_START = START_OF_SERVICE.plusHours(10);
  private static final Duration UNTIL_PICKUP = Duration.ofMinutes(30);
  private static final Duration RIDE = Duration.ofMinutes(15);

  @Test
  void feasibleDepartAfterYieldsAbsoluteTimes() {
    var result = evaluate(
      TOUR_START,
      timeAt(9, 50),
      false,
      Duration.ZERO,
      Duration.ZERO,
      RoutingBookingInfo.unrestricted()
    );

    assertThat(result).isPresent();
    assertThat(result.get().pickupTime()).isEqualTo(START_OF_SERVICE.plusHours(10).plusMinutes(30));
    assertThat(result.get().dropoffTime()).isEqualTo(
      START_OF_SERVICE.plusHours(10).plusMinutes(45)
    );
  }

  @Test
  void rejectsWhenVehicleReachesPickupBeforeRequestedDeparture() {
    var result = evaluate(
      TOUR_START,
      timeAt(11, 0),
      false,
      Duration.ZERO,
      Duration.ZERO,
      RoutingBookingInfo.unrestricted()
    );

    assertThat(result).isEmpty();
  }

  @Test
  void walkToPickupCountsAgainstTheRequestedDeparture() {
    // The vehicle reaches the pickup 10:30; a passenger departing 10:25 makes it with no walk...
    var withoutWalk = evaluate(
      TOUR_START,
      timeAt(10, 25),
      false,
      Duration.ZERO,
      Duration.ZERO,
      RoutingBookingInfo.unrestricted()
    );
    assertThat(withoutWalk).isPresent();

    // ...but not with a 10-minute walk: they would have to leave at 10:20, before 10:25.
    var withWalk = evaluate(
      TOUR_START,
      timeAt(10, 25),
      false,
      Duration.ofMinutes(10),
      Duration.ZERO,
      RoutingBookingInfo.unrestricted()
    );
    assertThat(withWalk).isEmpty();
  }

  @Test
  void arriveByAcceptsAndRejectsOnTheJourneyEnd() {
    // Dropoff 10:45 + 10 min walk = 10:55.
    var accepted = evaluate(
      TOUR_START,
      timeAt(11, 0),
      true,
      Duration.ZERO,
      Duration.ofMinutes(10),
      RoutingBookingInfo.unrestricted()
    );
    assertThat(accepted).isPresent();

    var rejected = evaluate(
      TOUR_START,
      timeAt(10, 50),
      true,
      Duration.ZERO,
      Duration.ofMinutes(10),
      RoutingBookingInfo.unrestricted()
    );
    assertThat(rejected).isEmpty();
  }

  @Test
  void rejectsPickupBeforeTheStopTimeWindow() {
    // Tour starting 09:00 reaches the pickup 09:30, before the window opens at 10:00.
    var result = evaluate(
      START_OF_SERVICE.plusHours(9),
      timeAt(8, 0),
      false,
      Duration.ZERO,
      Duration.ZERO,
      RoutingBookingInfo.unrestricted()
    );

    assertThat(result).isEmpty();
  }

  @Test
  void rejectsDropoffAfterTheStopTimeWindow() {
    // Tour starting 13:20 reaches the pickup 13:50 (in window) but the dropoff 14:05 (outside).
    var result = evaluate(
      START_OF_SERVICE.plusHours(13).plusMinutes(20),
      timeAt(13, 0),
      false,
      Duration.ZERO,
      Duration.ZERO,
      RoutingBookingInfo.unrestricted()
    );

    assertThat(result).isEmpty();
  }

  @Test
  void rejectsPickupViolatingTheMinimumBookingNotice() {
    // Booking at 10:15 with a 30-minute notice allows boarding from 10:45; pickup is 10:30.
    var bookingInfo = RoutingBookingInfo.of(10 * 3600 + 15 * 60)
      .withMinimumBookingNotice(Duration.ofMinutes(30))
      .build();

    var result = evaluate(
      TOUR_START,
      timeAt(9, 50),
      false,
      Duration.ZERO,
      Duration.ZERO,
      bookingInfo
    );

    assertThat(result).isEmpty();
  }

  private static Optional<FlexInsertionFeasibility.TimedInsertion> evaluate(
    ZonedDateTime tourStart,
    Instant requestedTime,
    boolean arriveBy,
    Duration walkToPickup,
    Duration walkFromDropoff,
    RoutingBookingInfo bookingInfo
  ) {
    return FlexInsertionFeasibility.evaluate(
      tourStart,
      UNTIL_PICKUP,
      RIDE,
      TRIP,
      BOARD_POS,
      ALIGHT_POS,
      START_OF_SERVICE,
      requestedTime,
      arriveBy,
      walkToPickup,
      walkFromDropoff,
      bookingInfo
    );
  }

  private static Instant timeAt(int hour, int minute) {
    return START_OF_SERVICE.plusHours(hour).plusMinutes(minute).toInstant();
  }
}
