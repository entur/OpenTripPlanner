package org.opentripplanner.updater.trip.gtfs.moduletests.cancellation;

import static com.google.common.truth.Truth.assertThat;
import static com.google.transit.realtime.GtfsRealtime.TripDescriptor.ScheduleRelationship.ADDED;
import static com.google.transit.realtime.GtfsRealtime.TripDescriptor.ScheduleRelationship.REPLACEMENT;
import static org.opentripplanner.updater.spi.UpdateResultAssertions.assertSuccess;

import org.junit.jupiter.api.Test;
import org.opentripplanner.model.PickDrop;
import org.opentripplanner.transit.model.TransitTestEnvironment;
import org.opentripplanner.transit.model.TransitTestEnvironmentBuilder;
import org.opentripplanner.transit.model.TripInput;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.site.RegularStop;
import org.opentripplanner.updater.trip.RealtimeTestConstants;
import org.opentripplanner.updater.trip.gtfs.GtfsRtTestHelper;

/**
 * An update that skips every stop of a GTFS-RT trip does not cancel it. Only the schedule
 * relationship of the trip itself - {@code CANCELED} or {@code DELETED} - cancels a GTFS-RT trip;
 * a skipped call cancels that call, and a trip whose every call is cancelled still runs, it just
 * cannot be used anywhere. Reading a cancellation out of the resulting pattern is the SIRI-ET rule
 * (see {@code CancelAllStopsTest} on that side), and it must not leak into the GTFS-RT path.
 */
class AllStopsSkippedTest implements RealtimeTestConstants {

  private final TransitTestEnvironmentBuilder ENV_BUILDER = TransitTestEnvironment.of();
  private final RegularStop STOP_A = ENV_BUILDER.stop(STOP_A_ID);
  private final RegularStop STOP_B = ENV_BUILDER.stop(STOP_B_ID);

  private final TripInput TRIP_INPUT = TripInput.of(TRIP_1_ID)
    .addStop(STOP_A, "0:01:00", "0:01:01")
    .addStop(STOP_B, "0:01:10", "0:01:11");

  /**
   * The trip stays UPDATED on a real-time pattern where no stop can be boarded or alighted. It
   * carries its scheduled times: the update times none of its calls, and a skipped call is the one
   * kind of call the forwards interpolator leaves to the scheduled timetable.
   */
  @Test
  void anUpdateThatSkipsEveryStopDoesNotCancelTheTrip() {
    var env = ENV_BUILDER.addTrip(TRIP_INPUT).build();
    var rt = GtfsRtTestHelper.of(env);

    var tripUpdate = rt.tripUpdateScheduled(TRIP_1_ID).addSkippedStop(0).addSkippedStop(1).build();

    assertSuccess(rt.applyTripUpdate(tripUpdate));

    var tripData = env.tripData(TRIP_1_ID);
    assertThat(tripData.tripTimes().isCanceledOrDeleted()).isFalse();
    assertThat(tripData.showTimetable()).isEqualTo(
      "U | A [C] 0:01 0:01:01 | B [C] 0:01:10 0:01:11"
    );

    // The trip moves off its scheduled pattern, because the cancelled calls change the pattern -
    // publishing it on the scheduled pattern instead would let it be boarded there.
    assertThat(tripData.tripPattern()).isNotSameInstanceAs(tripData.scheduledTripPattern());
    assertAllStopsCancelled(tripData.tripPattern());
  }

  /** An added trip whose every call is skipped is added, not cancelled. */
  @Test
  void anAddedTripThatSkipsEveryCallIsNotCancelled() {
    var env = ENV_BUILDER.addTrip(TRIP_INPUT).build();
    var rt = GtfsRtTestHelper.of(env);

    var tripUpdate = rt
      .tripUpdate(ADDED_TRIP_ID, ADDED)
      .addSkippedStop(STOP_A_ID, "12:00")
      .addSkippedStop(STOP_B_ID, "12:10")
      .build();

    assertSuccess(rt.applyTripUpdate(tripUpdate));

    var tripData = env.tripData(ADDED_TRIP_ID);
    assertThat(tripData.tripTimes().isAdded()).isTrue();
    assertThat(tripData.tripTimes().isCanceledOrDeleted()).isFalse();
    assertAllStopsCancelled(tripData.tripPattern());
  }

  /** A replacement trip whose every call is skipped keeps its modified pattern, not cancelled. */
  @Test
  void aReplacementTripThatSkipsEveryCallIsNotCancelled() {
    var env = ENV_BUILDER.addTrip(TRIP_INPUT).build();
    var rt = GtfsRtTestHelper.of(env);

    var tripUpdate = rt
      .tripUpdate(TRIP_1_ID, REPLACEMENT)
      .addSkippedStop(STOP_A_ID, "0:01:01")
      .addSkippedStop(STOP_B_ID, "0:01:11")
      .build();

    assertSuccess(rt.applyTripUpdate(tripUpdate));

    var tripData = env.tripData(TRIP_1_ID);
    assertThat(tripData.tripTimes().isTripPatternModified()).isTrue();
    assertThat(tripData.tripTimes().isCanceledOrDeleted()).isFalse();
    assertAllStopsCancelled(tripData.tripPattern());
  }

  private static void assertAllStopsCancelled(TripPattern pattern) {
    for (int i = 0; i < pattern.numberOfStops(); i++) {
      assertThat(pattern.getBoardType(i)).isEqualTo(PickDrop.CANCELLED);
      assertThat(pattern.getAlightType(i)).isEqualTo(PickDrop.CANCELLED);
    }
  }
}
