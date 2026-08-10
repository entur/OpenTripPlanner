package org.opentripplanner.updater.trip.gtfs.moduletests.rejection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opentripplanner.updater.spi.UpdateErrorType.NO_UPDATES;
import static org.opentripplanner.updater.spi.UpdateResultAssertions.assertFailure;
import static org.opentripplanner.updater.spi.UpdateResultAssertions.assertSuccess;
import static org.opentripplanner.updater.trip.UpdateIncrementality.DIFFERENTIAL;

import org.junit.jupiter.api.Test;
import org.opentripplanner.transit.model.TransitTestEnvironment;
import org.opentripplanner.transit.model.TransitTestEnvironmentBuilder;
import org.opentripplanner.transit.model.TripInput;
import org.opentripplanner.transit.model.site.RegularStop;
import org.opentripplanner.updater.trip.RealtimeTestConstants;
import org.opentripplanner.updater.trip.gtfs.GtfsRtTestHelper;

/**
 * A SCHEDULED trip update without any stop time updates revises nothing, so it is rejected with
 * {@code NO_UPDATES} and must leave previously applied real-time data alone.
 */
class NoUpdatesTest implements RealtimeTestConstants {

  private final TransitTestEnvironmentBuilder ENV_BUILDER = TransitTestEnvironment.of();
  private final RegularStop STOP_A = ENV_BUILDER.stop(STOP_A_ID);
  private final RegularStop STOP_B = ENV_BUILDER.stop(STOP_B_ID);

  private TransitTestEnvironment env() {
    var tripInput = TripInput.of(TRIP_1_ID)
      .addStop(STOP_A, "0:00:10", "0:00:11")
      .addStop(STOP_B, "0:00:20", "0:00:21");
    return ENV_BUILDER.addTrip(tripInput).build();
  }

  @Test
  void emptyUpdateIsRejected() {
    var env = env();
    var rt = GtfsRtTestHelper.of(env);

    var update = rt.tripUpdateScheduled(TRIP_1_ID).build();

    var result = rt.applyTripUpdate(update);

    assertFailure(NO_UPDATES, result);
    assertTrue(env.timetableSnapshot().isEmpty());
  }

  @Test
  void emptyUpdateLeavesPreviousRealTimeDataAlone() {
    var env = env();
    var rt = GtfsRtTestHelper.of(env);

    var delayed = rt.tripUpdateScheduled(TRIP_1_ID).addDelayedStopTime(1, 60).build();
    assertSuccess(rt.applyTripUpdate(delayed));

    var delayedTimetable = "U | A [ND] 0:00:10 0:00:11 | B 0:01:20 0:01:21";
    assertEquals(delayedTimetable, env.tripData(TRIP_1_ID).showTimetable());

    var empty = rt.tripUpdateScheduled(TRIP_1_ID).build();
    var result = rt.applyTripUpdate(empty, DIFFERENTIAL);

    assertFailure(NO_UPDATES, result);
    assertEquals(delayedTimetable, env.tripData(TRIP_1_ID).showTimetable());
  }
}
