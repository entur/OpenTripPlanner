package org.opentripplanner.updater.trip.gtfs.moduletests.rejection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opentripplanner.updater.spi.UpdateErrorType.INVALID_ARRIVAL_TIME;
import static org.opentripplanner.updater.spi.UpdateErrorType.INVALID_DEPARTURE_TIME;
import static org.opentripplanner.updater.spi.UpdateResultAssertions.assertFailure;
import static org.opentripplanner.updater.spi.UpdateResultAssertions.assertSuccess;
import static org.opentripplanner.updater.trip.UpdateIncrementality.DIFFERENTIAL;

import com.google.transit.realtime.GtfsRealtime.TripUpdate.StopTimeEvent;
import com.google.transit.realtime.GtfsRealtime.TripUpdate.StopTimeUpdate;
import org.junit.jupiter.api.Test;
import org.opentripplanner.transit.model.TransitTestEnvironment;
import org.opentripplanner.transit.model.TransitTestEnvironmentBuilder;
import org.opentripplanner.transit.model.TripInput;
import org.opentripplanner.transit.model.site.RegularStop;
import org.opentripplanner.updater.trip.RealtimeTestConstants;
import org.opentripplanner.updater.trip.gtfs.GtfsRtTestHelper;

/**
 * An arrival or departure of a SCHEDULED trip update must state a time or a delay. An event that
 * states neither is a producer error and rejects the whole entity - it must not be treated as an
 * unreported call and filled in by interpolation.
 */
class InvalidStopTimeEventTest implements RealtimeTestConstants {

  private final TransitTestEnvironmentBuilder envBuilder = TransitTestEnvironment.of();
  private final RegularStop stopA = envBuilder.stop(STOP_A_ID);
  private final RegularStop stopB = envBuilder.stop(STOP_B_ID);
  private final RegularStop stopC = envBuilder.stop(STOP_C_ID);
  private final TripInput tripInput = TripInput.of(TRIP_1_ID)
    .addStop(stopA, "10:00", "10:00")
    .addStop(stopB, "10:10", "10:10")
    .addStop(stopC, "10:20", "10:20");

  @Test
  void emptyArrivalEvent() {
    var env = envBuilder.addTrip(tripInput).build();
    var rt = GtfsRtTestHelper.of(env);

    var update = rt
      .tripUpdateScheduled(TRIP_1_ID)
      .addRawStopTime(emptyEventStopTime(1, true))
      .build();

    assertFailure(INVALID_ARRIVAL_TIME, rt.applyTripUpdate(update));
  }

  @Test
  void emptyDepartureEvent() {
    var env = envBuilder.addTrip(tripInput).build();
    var rt = GtfsRtTestHelper.of(env);

    var update = rt
      .tripUpdateScheduled(TRIP_1_ID)
      .addRawStopTime(emptyEventStopTime(1, false))
      .build();

    assertFailure(INVALID_DEPARTURE_TIME, rt.applyTripUpdate(update));
  }

  /**
   * One good call does not save the entity: the empty event on the other call rejects the whole
   * update and nothing - not even the good call - is published.
   */
  @Test
  void oneEmptyEventRejectsTheWholeEntity() {
    var env = envBuilder.addTrip(tripInput).build();
    var rt = GtfsRtTestHelper.of(env);

    var update = rt
      .tripUpdateScheduled(TRIP_1_ID)
      .addDelayedStopTime(1, 60)
      .addRawStopTime(emptyEventStopTime(2, true))
      .build();

    assertFailure(INVALID_ARRIVAL_TIME, rt.applyTripUpdate(update));
    assertTrue(env.timetableSnapshot().isEmpty());
  }

  /**
   * A rejected entity leaves real-time data published by an earlier update alone.
   */
  @Test
  void rejectedEntityLeavesPriorRealTimeDataAlone() {
    var env = envBuilder.addTrip(tripInput).build();
    var rt = GtfsRtTestHelper.of(env);

    var goodUpdate = rt.tripUpdateScheduled(TRIP_1_ID).addDelayedStopTime(1, 60).build();
    assertSuccess(rt.applyTripUpdate(goodUpdate, DIFFERENTIAL));

    var badUpdate = rt
      .tripUpdateScheduled(TRIP_1_ID)
      .addDelayedStopTime(1, 120)
      .addRawStopTime(emptyEventStopTime(2, true))
      .build();
    assertFailure(INVALID_ARRIVAL_TIME, rt.applyTripUpdate(badUpdate, DIFFERENTIAL));

    assertEquals(60, env.tripData(TRIP_1_ID).tripTimes().getDepartureDelay(1));
  }

  /**
   * A call of a SCHEDULED trip update reporting only a scheduled time states no prediction at all.
   * Only trips that bring their own schedule - NEW, REPLACEMENT, DUPLICATED - may do that.
   */
  @Test
  void scheduledTimeOnlyEventIsRejectedForScheduledTrip() {
    var env = envBuilder.addTrip(tripInput).build();
    var rt = GtfsRtTestHelper.of(env);

    var update = rt
      .tripUpdateScheduled(TRIP_1_ID)
      .addStopTimeWithOnlyScheduled(STOP_B_ID, "10:10")
      .build();

    assertFailure(INVALID_ARRIVAL_TIME, rt.applyTripUpdate(update));
  }

  /**
   * The check applies only to calls the trip is predicted to make: a SKIPPED call carries no
   * prediction, so an empty event on it is not an error.
   */
  @Test
  void skippedStopWithEmptyEventIsAccepted() {
    var env = envBuilder.addTrip(tripInput).build();
    var rt = GtfsRtTestHelper.of(env);

    var update = rt
      .tripUpdateScheduled(TRIP_1_ID)
      .addRawStopTime(
        StopTimeUpdate.newBuilder()
          .setStopSequence(1)
          .setScheduleRelationship(StopTimeUpdate.ScheduleRelationship.SKIPPED)
          .setArrival(StopTimeEvent.newBuilder().build())
          .build()
      )
      .addDelayedStopTime(2, 60)
      .build();

    assertSuccess(rt.applyTripUpdate(update));
  }

  private static StopTimeUpdate emptyEventStopTime(int stopSequence, boolean arrival) {
    var emptyEvent = StopTimeEvent.newBuilder().build();
    var stopTime = StopTimeUpdate.newBuilder().setStopSequence(stopSequence);
    return (arrival ? stopTime.setArrival(emptyEvent) : stopTime.setDeparture(emptyEvent)).build();
  }
}
