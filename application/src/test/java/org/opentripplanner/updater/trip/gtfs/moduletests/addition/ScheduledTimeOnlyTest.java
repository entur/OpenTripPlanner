package org.opentripplanner.updater.trip.gtfs.moduletests.addition;

import static com.google.transit.realtime.GtfsRealtime.TripDescriptor.ScheduleRelationship.ADDED;
import static com.google.transit.realtime.GtfsRealtime.TripDescriptor.ScheduleRelationship.REPLACEMENT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opentripplanner.updater.spi.UpdateResultAssertions.assertSuccess;

import org.junit.jupiter.api.Test;
import org.opentripplanner.transit.model.TransitTestEnvironment;
import org.opentripplanner.transit.model.TransitTestEnvironmentBuilder;
import org.opentripplanner.transit.model.TripInput;
import org.opentripplanner.transit.model.site.RegularStop;
import org.opentripplanner.transit.model.timetable.TripTimes;
import org.opentripplanner.updater.trip.RealtimeTestConstants;
import org.opentripplanner.updater.trip.gtfs.GtfsRtTestHelper;
import org.opentripplanner.utils.time.TimeUtils;

/**
 * A trip that brings its own schedule with it - NEW, ADDED or REPLACEMENT - is the only source of
 * its own scheduled times, so the specification lets its calls report a {@code scheduled_time}
 * without any prediction. Such a call still has to produce a stop time: the trip is published as
 * running to its own schedule, on time.
 * <p>
 * The stop times of these trips are therefore taken from {@code scheduled_time}, falling back to
 * {@code time - delay}, and the prediction is applied on top of that - never the other way around.
 */
class ScheduledTimeOnlyTest implements RealtimeTestConstants {

  private final TransitTestEnvironmentBuilder envBuilder = TransitTestEnvironment.of();
  private final RegularStop stopA = envBuilder.stop(STOP_A_ID);
  private final RegularStop stopB = envBuilder.stop(STOP_B_ID);
  private final RegularStop stopC = envBuilder.stop(STOP_C_ID);

  private final TransitTestEnvironment env = envBuilder
    .addTrip(
      TripInput.of(TRIP_1_ID)
        .addStop(stopA, "8:30", "8:30")
        .addStop(stopB, "8:40", "8:40")
        .addStop(stopC, "8:50", "8:50")
    )
    .build();
  private final GtfsRtTestHelper gtfsRt = GtfsRtTestHelper.of(env);

  @Test
  void addedTripWithOnlyScheduledTimes() {
    var tripUpdate = gtfsRt
      .tripUpdate(ADDED_TRIP_ID, ADDED)
      .addStopTimeWithOnlyScheduled(STOP_A_ID, "12:00")
      .addStopTimeWithOnlyScheduled(STOP_B_ID, "12:10")
      .addStopTimeWithOnlyScheduled(STOP_C_ID, "12:20")
      .build();

    assertSuccess(gtfsRt.applyTripUpdate(tripUpdate));

    var tripTimes = env.tripData(ADDED_TRIP_ID).tripTimes();
    assertTrue(tripTimes.isAdded());
    assertTimes(tripTimes, 0, "12:00");
    assertTimes(tripTimes, 1, "12:10");
    assertTimes(tripTimes, 2, "12:20");
  }

  @Test
  void replacementTripWithOnlyScheduledTimes() {
    var tripUpdate = gtfsRt
      .tripUpdate(TRIP_1_ID, REPLACEMENT)
      .addStopTimeWithOnlyScheduled(STOP_A_ID, "9:30")
      .addStopTimeWithOnlyScheduled(STOP_C_ID, "9:45")
      .addStopTimeWithOnlyScheduled(STOP_B_ID, "10:00")
      .build();

    assertSuccess(gtfsRt.applyTripUpdate(tripUpdate));

    var tripTimes = env.tripData(TRIP_1_ID).tripTimes();
    assertTrue(tripTimes.isTripPatternModified());
    assertTimes(tripTimes, 0, "9:30");
    assertTimes(tripTimes, 1, "9:45");
    assertTimes(tripTimes, 2, "10:00");
  }

  /**
   * The scheduled time and the prediction are two separate values, so a trip may report a
   * prediction for the calls it has one for and only a scheduled time for the rest.
   */
  @Test
  void addedTripWithPredictionForSomeCallsOnly() {
    var tripUpdate = gtfsRt
      .tripUpdate(ADDED_TRIP_ID, ADDED)
      .addStopTimeWithOnlyScheduled(STOP_A_ID, "12:00")
      .addStopTimeWithScheduled(STOP_B_ID, "12:15", "12:10")
      .addStopTimeWithOnlyScheduled(STOP_C_ID, "12:20")
      .build();

    assertSuccess(gtfsRt.applyTripUpdate(tripUpdate));

    var tripTimes = env.tripData(ADDED_TRIP_ID).tripTimes();
    assertTimes(tripTimes, 0, "12:00");

    // the only call with a prediction is five minutes late against its own reported schedule
    assertEquals(TimeUtils.time("12:10"), tripTimes.getScheduledArrivalTime(1));
    assertEquals(TimeUtils.time("12:15"), tripTimes.getArrivalTime(1));
    assertEquals(300, tripTimes.getArrivalDelay(1));

    // the delay does not spread to a call that reported none
    assertTimes(tripTimes, 2, "12:20");
  }

  /**
   * A call without a prediction runs to the time it reported itself, so its scheduled and real-time
   * values are the same and it is neither early nor late.
   */
  private static void assertTimes(TripTimes tripTimes, int stopPos, String time) {
    int expected = TimeUtils.time(time);
    assertEquals(expected, tripTimes.getScheduledArrivalTime(stopPos));
    assertEquals(expected, tripTimes.getScheduledDepartureTime(stopPos));
    assertEquals(expected, tripTimes.getArrivalTime(stopPos));
    assertEquals(expected, tripTimes.getDepartureTime(stopPos));
    assertEquals(0, tripTimes.getArrivalDelay(stopPos));
    assertEquals(0, tripTimes.getDepartureDelay(stopPos));
  }
}
