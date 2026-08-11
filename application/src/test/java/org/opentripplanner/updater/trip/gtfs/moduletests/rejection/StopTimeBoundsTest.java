package org.opentripplanner.updater.trip.gtfs.moduletests.rejection;

import static com.google.transit.realtime.GtfsRealtime.TripDescriptor.ScheduleRelationship.ADDED;
import static com.google.transit.realtime.GtfsRealtime.TripDescriptor.ScheduleRelationship.NEW;
import static com.google.transit.realtime.GtfsRealtime.TripDescriptor.ScheduleRelationship.REPLACEMENT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opentripplanner.updater.spi.UpdateErrorType.INVALID_ARRIVAL_TIME;
import static org.opentripplanner.updater.spi.UpdateErrorType.INVALID_DEPARTURE_TIME;
import static org.opentripplanner.updater.spi.UpdateResultAssertions.assertFailure;
import static org.opentripplanner.updater.spi.UpdateResultAssertions.assertSuccess;

import org.junit.jupiter.api.Test;
import org.opentripplanner.transit.model.TransitTestEnvironment;
import org.opentripplanner.transit.model.TransitTestEnvironmentBuilder;
import org.opentripplanner.transit.model.TripInput;
import org.opentripplanner.transit.model.site.RegularStop;
import org.opentripplanner.updater.trip.RealtimeTestConstants;
import org.opentripplanner.updater.trip.gtfs.GtfsRtTestHelper;
import org.opentripplanner.utils.time.TimeUtils;

/**
 * A NEW, ADDED or REPLACEMENT trip brings its own schedule with it, and every call of that
 * schedule must lie within 48 hours after the start of the service day the message names. A time
 * before the start of service or past that limit is a producer error - typically a timestamp on
 * the wrong day - and publishing it would serve the trip on the wrong service day, so the entity
 * is rejected.
 */
class StopTimeBoundsTest implements RealtimeTestConstants {

  private final TransitTestEnvironmentBuilder envBuilder = TransitTestEnvironment.of();
  private final RegularStop stopA = envBuilder.stop(STOP_A_ID);
  private final RegularStop stopB = envBuilder.stop(STOP_B_ID);
  private final TransitTestEnvironment env = envBuilder
    .addTrip(
      TripInput.of(TRIP_1_ID).addStop(stopA, "10:00", "10:00").addStop(stopB, "10:10", "10:10")
    )
    .build();
  private final GtfsRtTestHelper gtfsRt = GtfsRtTestHelper.of(env);

  @Test
  void addedTripWithTimePast48HoursIsRejected() {
    var tripUpdate = gtfsRt
      .tripUpdate(ADDED_TRIP_ID, ADDED)
      .addStopTime(STOP_A_ID, "00:30")
      .addStopTime(STOP_B_ID, "73:00")
      .build();

    assertFailure(INVALID_ARRIVAL_TIME, gtfsRt.applyTripUpdate(tripUpdate));
    assertTrue(env.timetableSnapshot().isEmpty());
  }

  @Test
  void addedTripWithTimeBeforeStartOfServiceIsRejected() {
    var tripUpdate = gtfsRt
      .tripUpdate(ADDED_TRIP_ID, ADDED)
      .addStopTime(STOP_A_ID, "-1:00")
      .addStopTime(STOP_B_ID, "00:30")
      .build();

    assertFailure(INVALID_ARRIVAL_TIME, gtfsRt.applyTripUpdate(tripUpdate));
    assertTrue(env.timetableSnapshot().isEmpty());
  }

  @Test
  void addedTripWithOnlyTheDepartureOutOfBoundsIsRejectedForTheDeparture() {
    var tripUpdate = gtfsRt
      .tripUpdate(ADDED_TRIP_ID, ADDED)
      .addStopTime(STOP_A_ID, "46:00")
      .addStopTimeWithArrivalAndDeparture(STOP_B_ID, "47:00", "49:00")
      .build();

    assertFailure(INVALID_DEPARTURE_TIME, gtfsRt.applyTripUpdate(tripUpdate));
    assertTrue(env.timetableSnapshot().isEmpty());
  }

  /**
   * The bounds apply to the schedule the trip brings, which a call reporting a predicted time and
   * a delay places at {@code time - delay}: an in-bounds prediction whose delay puts the derived
   * scheduled time before the start of service is just as wrong as a bad timestamp.
   */
  @Test
  void addedTripWithDelayPushingTheScheduledTimeNegativeIsRejected() {
    var tripUpdate = gtfsRt
      .tripUpdate(ADDED_TRIP_ID, ADDED)
      .addStopTimeWithDelay(STOP_A_ID, "00:30", 7200)
      .addStopTime(STOP_B_ID, "00:40")
      .build();

    assertFailure(INVALID_ARRIVAL_TIME, gtfsRt.applyTripUpdate(tripUpdate));
    assertTrue(env.timetableSnapshot().isEmpty());
  }

  @Test
  void newTripWithScheduledTimeOnlyCallOutOfBoundsIsRejected() {
    var tripUpdate = gtfsRt
      .tripUpdate(ADDED_TRIP_ID, NEW)
      .addStopTimeWithOnlyScheduled(STOP_A_ID, "00:30")
      .addStopTimeWithOnlyScheduled(STOP_B_ID, "73:00")
      .build();

    assertFailure(INVALID_ARRIVAL_TIME, gtfsRt.applyTripUpdate(tripUpdate));
    assertTrue(env.timetableSnapshot().isEmpty());
  }

  @Test
  void replacementTripWithTimePast48HoursIsRejected() {
    var tripUpdate = gtfsRt
      .tripUpdate(TRIP_1_ID, REPLACEMENT)
      .addStopTime(STOP_A_ID, "00:30")
      .addStopTime(STOP_B_ID, "73:00")
      .build();

    assertFailure(INVALID_ARRIVAL_TIME, gtfsRt.applyTripUpdate(tripUpdate));
    assertTrue(env.timetableSnapshot().isEmpty());
  }

  /** The limit itself is still within bounds: a trip may run to exactly 48:00. */
  @Test
  void addedTripEndingExactlyAtTheLimitIsAccepted() {
    var tripUpdate = gtfsRt
      .tripUpdate(ADDED_TRIP_ID, ADDED)
      .addStopTime(STOP_A_ID, "47:50")
      .addStopTime(STOP_B_ID, "48:00")
      .build();

    assertSuccess(gtfsRt.applyTripUpdate(tripUpdate));
    assertEquals(
      TimeUtils.time("48:00"),
      env.tripData(ADDED_TRIP_ID).tripTimes().getArrivalTime(1)
    );
  }
}
