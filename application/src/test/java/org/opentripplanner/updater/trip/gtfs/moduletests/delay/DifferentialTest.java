package org.opentripplanner.updater.trip.gtfs.moduletests.delay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.opentripplanner.updater.spi.UpdateResultAssertions.assertSuccess;
import static org.opentripplanner.updater.trip.UpdateIncrementality.DIFFERENTIAL;

import org.junit.jupiter.api.Test;
import org.opentripplanner.transit.model._data.TransitTestEnvironment;
import org.opentripplanner.transit.model._data.TransitTestEnvironmentBuilder;
import org.opentripplanner.transit.model._data.TripInput;
import org.opentripplanner.transit.model.site.RegularStop;
import org.opentripplanner.updater.trip.GtfsRtTestHelper;
import org.opentripplanner.updater.trip.RealtimeTestConstants;

/**
 * DIFFERENTIAL updates should preserve previously applied updates for other trips.
 */
class DifferentialTest implements RealtimeTestConstants {

  private final TransitTestEnvironmentBuilder ENV_BUILDER = TransitTestEnvironment.of();
  private final RegularStop STOP_A = ENV_BUILDER.stop(STOP_A_ID);
  private final RegularStop STOP_B = ENV_BUILDER.stop(STOP_B_ID);

  private static final int DELAY = 1;
  private static final int STOP_SEQUENCE = 1;

  @Test
  void differentialPreservesOtherTrips() {
    var trip1 = TripInput.of(TRIP_1_ID)
      .addStop(STOP_A, "0:00:10", "0:00:11")
      .addStop(STOP_B, "0:00:20", "0:00:21");
    var trip2 = TripInput.of(TRIP_2_ID)
      .addStop(STOP_A, "0:01:00", "0:01:01")
      .addStop(STOP_B, "0:01:10", "0:01:11");
    var env = ENV_BUILDER.addTrip(trip1).addTrip(trip2).build();
    var rt = GtfsRtTestHelper.of(env);

    var tripUpdate1 = rt
      .tripUpdateScheduled(TRIP_1_ID)
      .addDelayedStopTime(STOP_SEQUENCE, DELAY)
      .build();

    assertSuccess(rt.applyTripUpdate(tripUpdate1, DIFFERENTIAL));

    var tripUpdate2 = rt
      .tripUpdateScheduled(TRIP_2_ID)
      .addDelayedStopTime(STOP_SEQUENCE, DELAY)
      .build();

    assertSuccess(rt.applyTripUpdate(tripUpdate2, DIFFERENTIAL));

    assertEquals(
      "UPDATED | A [ND] 0:00:10 0:00:11 | B 0:00:21 0:00:22",
      env.tripData(TRIP_1_ID).showTimetable()
    );
    assertEquals(
      "UPDATED | A [ND] 0:01 0:01:01 | B 0:01:11 0:01:12",
      env.tripData(TRIP_2_ID).showTimetable()
    );
  }
}
