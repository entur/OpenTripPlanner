package org.opentripplanner.updater.trip.gtfs.moduletests.delay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opentripplanner.updater.spi.UpdateResultAssertions.assertSuccess;

import org.junit.jupiter.api.Test;
import org.opentripplanner.transit.model.TransitTestEnvironment;
import org.opentripplanner.transit.model.TransitTestEnvironmentBuilder;
import org.opentripplanner.transit.model.TripInput;
import org.opentripplanner.transit.model.site.RegularStop;
import org.opentripplanner.updater.trip.RealtimeTestConstants;
import org.opentripplanner.updater.trip.gtfs.GtfsRtTestHelper;

/**
 * A GTFS {@code stop_sequence} is the number the static feed gave the call in
 * {@code stop_times.txt}, not the position of the call in the pattern: it is only required to
 * increase along the trip, and real feeds number their calls from 1. The update must therefore be
 * applied to the call carrying that number, whatever position it holds.
 */
class StopSequenceMatchingTest implements RealtimeTestConstants {

  private static final int DELAY = 60;

  private final TransitTestEnvironmentBuilder envBuilder = TransitTestEnvironment.of();
  private final RegularStop stopA = envBuilder.stop(STOP_A_ID);
  private final RegularStop stopB = envBuilder.stop(STOP_B_ID);
  private final RegularStop stopC = envBuilder.stop(STOP_C_ID);

  @Test
  void oneBasedStopSequences() {
    var env = envBuilder.addTrip(tripNumbered(1, 2, 3)).build();
    var rt = GtfsRtTestHelper.of(env);

    var update = rt.tripUpdateScheduled(TRIP_1_ID).addDelayedStopTime(3, DELAY).build();

    assertSuccess(rt.applyTripUpdate(update));

    // The call numbered 3 is the last one, at position 2.
    assertDelayedFrom(env, 2);
  }

  @Test
  void sparseStopSequences() {
    var env = envBuilder.addTrip(tripNumbered(10, 20, 30)).build();
    var rt = GtfsRtTestHelper.of(env);

    var update = rt.tripUpdateScheduled(TRIP_1_ID).addDelayedStopTime(20, DELAY).build();

    assertSuccess(rt.applyTripUpdate(update));

    // The call numbered 20 is the middle one, at position 1.
    assertDelayedFrom(env, 1);
  }

  @Test
  void skippedStopByStopSequence() {
    var env = envBuilder.addTrip(tripNumbered(1, 2, 3)).build();
    var rt = GtfsRtTestHelper.of(env);

    var update = rt.tripUpdateScheduled(TRIP_1_ID).addSkippedStop(2).build();

    assertSuccess(rt.applyTripUpdate(update));

    // The call numbered 2 is the middle one, at position 1.
    var tripTimes = env.tripData(TRIP_1_ID).tripTimes();
    assertFalse(tripTimes.isCanceledStop(0));
    assertTrue(tripTimes.isCanceledStop(1));
    assertFalse(tripTimes.isCanceledStop(2));
  }

  private TripInput tripNumbered(int... stopSequences) {
    return TripInput.of(TRIP_1_ID)
      .withStopSequences(stopSequences)
      .addStop(stopA, "0:00:10", "0:00:11")
      .addStop(stopB, "0:00:20", "0:00:21")
      .addStop(stopC, "0:00:30", "0:00:31");
  }

  /**
   * The delay landed on {@code stopPosition}: every call before it is on time, and the delay is
   * propagated forwards from there. The position of the first delayed call is what identifies the
   * call the update was matched to.
   */
  private static void assertDelayedFrom(TransitTestEnvironment env, int stopPosition) {
    var tripTimes = env.tripData(TRIP_1_ID).tripTimes();
    for (int i = 0; i < 3; i++) {
      var expected = i < stopPosition ? 0 : DELAY;
      assertEquals(expected, tripTimes.getArrivalDelay(i), "arrival delay at position " + i);
      assertEquals(expected, tripTimes.getDepartureDelay(i), "departure delay at position " + i);
    }
  }
}
