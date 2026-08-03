package org.opentripplanner.updater.trip.gtfs.moduletests.delay;

import static com.google.transit.realtime.GtfsRealtime.TripDescriptor.ScheduleRelationship.ADDED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.opentripplanner.updater.spi.UpdateResultAssertions.assertSuccess;

import java.time.LocalDate;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.opentripplanner.transit.model.TransitTestEnvironment;
import org.opentripplanner.transit.model.TripInput;
import org.opentripplanner.updater.trip.RealtimeTestConstants;
import org.opentripplanner.updater.trip.gtfs.GtfsRtTestHelper;
import org.opentripplanner.utils.time.TimeUtils;

/**
 * A GTFS-RT {@code StopTimeEvent.time} is an absolute POSIX timestamp, so applying it means
 * subtracting the start of the service day. GTFS defines that origin as noon minus twelve hours,
 * <em>not</em> as calendar midnight — the two differ by exactly the offset shift on a service date
 * that contains a daylight-saving transition (see {@code ServiceDateUtils.asStartOfService}).
 * <p>
 * These tests pin that origin on both transitions in {@code Europe/Paris}: the 23-hour day in
 * spring and the 25-hour day in autumn. Using calendar midnight instead moves every absolute time
 * by an hour, in opposite directions on the two dates, while the control date is unaffected — which
 * is why the same times are asserted on all three dates.
 */
class DstServiceDateTest implements RealtimeTestConstants {

  /** Clocks jump 02:00 → 03:00, so the calendar day is 23 hours long. */
  private static final LocalDate SPRING_FORWARD = LocalDate.of(2024, 3, 31);

  /** Clocks fall back 03:00 → 02:00, so the calendar day is 25 hours long. */
  private static final LocalDate FALL_BACK = LocalDate.of(2024, 10, 27);

  /** Control: no transition, so calendar midnight and start of service coincide. */
  private static final LocalDate NO_TRANSITION = LocalDate.of(2024, 5, 7);

  private static Stream<Arguments> serviceDates() {
    return Stream.of(
      Arguments.of("spring forward (23-hour day)", SPRING_FORWARD),
      Arguments.of("fall back (25-hour day)", FALL_BACK),
      Arguments.of("no transition", NO_TRANSITION)
    );
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("serviceDates")
  void absoluteTimesOnScheduledTrip(String name, LocalDate serviceDate) {
    var envBuilder = TransitTestEnvironment.of(serviceDate);
    var stopA = envBuilder.stop(STOP_A_ID);
    var stopB = envBuilder.stop(STOP_B_ID);
    var env = envBuilder
      .addTrip(TripInput.of(TRIP_1_ID).addStop(stopA, "10:00").addStop(stopB, "10:10"))
      .build();
    var rt = GtfsRtTestHelper.of(env);

    var tripUpdate = rt
      .tripUpdateScheduled(TRIP_1_ID)
      .addStopTime(STOP_A_ID, "10:01")
      .addStopTime(STOP_B_ID, "10:11")
      .build();

    assertSuccess(rt.applyTripUpdate(tripUpdate));

    assertEquals("U | A 10:01 10:01 | B 10:11 10:11", env.tripData(TRIP_1_ID).showTimetable());
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("serviceDates")
  void absoluteTimesOnAddedTrip(String name, LocalDate serviceDate) {
    var envBuilder = TransitTestEnvironment.of(serviceDate);
    var stopA = envBuilder.stop(STOP_A_ID);
    var stopB = envBuilder.stop(STOP_B_ID);
    var env = envBuilder
      .addTrip(TripInput.of(TRIP_1_ID).addStop(stopA, "10:00").addStop(stopB, "10:10"))
      .build();
    var rt = GtfsRtTestHelper.of(env);

    var tripUpdate = rt
      .tripUpdate(ADDED_TRIP_ID, ADDED)
      .addStopTime(STOP_A_ID, "12:01")
      .addStopTime(STOP_B_ID, "12:11")
      .build();

    assertSuccess(rt.applyTripUpdate(tripUpdate));

    var tripTimes = env.tripData(ADDED_TRIP_ID).tripTimes();
    assertEquals(TimeUtils.time("12:01"), tripTimes.getDepartureTime(0));
    assertEquals(TimeUtils.time("12:11"), tripTimes.getArrivalTime(1));
  }
}
