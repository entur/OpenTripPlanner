package org.opentripplanner.updater.trip.gtfs.moduletests.addition;

import static com.google.transit.realtime.GtfsRealtime.TripDescriptor.ScheduleRelationship.ADDED;
import static com.google.transit.realtime.GtfsRealtime.TripDescriptor.ScheduleRelationship.REPLACEMENT;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
 * Midnight of the service date is a time a trip can be scheduled at, so a call that reports it as
 * its {@code scheduled_time} has reported a scheduled time like any other. A trip bringing its own
 * schedule is the only source of that schedule, so the value has to survive into the timetable the
 * prediction is measured against - otherwise the trip is published as running on time at the
 * predicted time rather than late against midnight.
 */
class MidnightScheduledTimeTest implements RealtimeTestConstants {

  private final TransitTestEnvironmentBuilder envBuilder = TransitTestEnvironment.of();
  private final RegularStop stopA = envBuilder.stop(STOP_A_ID);
  private final RegularStop stopB = envBuilder.stop(STOP_B_ID);
  private final RegularStop stopC = envBuilder.stop(STOP_C_ID);

  private final TransitTestEnvironment env = envBuilder
    .addTrip(
      TripInput.of(TRIP_1_ID)
        .addStop(stopA, "12:00", "12:00")
        .addStop(stopB, "12:10", "12:10")
        .addStop(stopC, "12:20", "12:20")
    )
    .build();
  private final GtfsRtTestHelper gtfsRt = GtfsRtTestHelper.of(env);

  @Test
  void addedTripScheduledAtMidnight() {
    var tripUpdate = gtfsRt
      .tripUpdate(ADDED_TRIP_ID, ADDED)
      .addStopTimeWithScheduled(STOP_A_ID, "00:03", "00:00")
      .addStopTimeWithScheduled(STOP_B_ID, "00:13", "00:10")
      .addStopTimeWithScheduled(STOP_C_ID, "00:23", "00:20")
      .build();

    assertSuccess(gtfsRt.applyTripUpdate(tripUpdate));

    var tripTimes = env.tripData(ADDED_TRIP_ID).tripTimes();
    assertLateByThreeMinutes(tripTimes, 0, "00:00");
    assertLateByThreeMinutes(tripTimes, 1, "00:10");
    assertLateByThreeMinutes(tripTimes, 2, "00:20");
  }

  @Test
  void replacementTripScheduledAtMidnight() {
    var tripUpdate = gtfsRt
      .tripUpdate(TRIP_1_ID, REPLACEMENT)
      .addStopTimeWithScheduled(STOP_A_ID, "00:03", "00:00")
      .addStopTimeWithScheduled(STOP_C_ID, "00:13", "00:10")
      .addStopTimeWithScheduled(STOP_B_ID, "00:23", "00:20")
      .build();

    assertSuccess(gtfsRt.applyTripUpdate(tripUpdate));

    var tripTimes = env.tripData(TRIP_1_ID).tripTimes();
    assertLateByThreeMinutes(tripTimes, 0, "00:00");
    assertLateByThreeMinutes(tripTimes, 1, "00:10");
    assertLateByThreeMinutes(tripTimes, 2, "00:20");
  }

  private static void assertLateByThreeMinutes(
    TripTimes tripTimes,
    int stopPos,
    String scheduledTime
  ) {
    int scheduled = TimeUtils.time(scheduledTime);
    assertEquals(scheduled, tripTimes.getScheduledArrivalTime(stopPos));
    assertEquals(scheduled, tripTimes.getScheduledDepartureTime(stopPos));
    assertEquals(scheduled + 180, tripTimes.getArrivalTime(stopPos));
    assertEquals(scheduled + 180, tripTimes.getDepartureTime(stopPos));
    assertEquals(180, tripTimes.getArrivalDelay(stopPos));
    assertEquals(180, tripTimes.getDepartureDelay(stopPos));
  }
}
