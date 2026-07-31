package org.opentripplanner.updater.trip.gtfs.moduletests.cancellation;

import static com.google.common.truth.Truth.assertThat;
import static org.opentripplanner.updater.spi.UpdateResultAssertions.assertSuccess;

import com.google.transit.realtime.GtfsRealtime.TripUpdate.StopTimeUpdate.StopTimeProperties.DropOffPickupType;
import org.junit.jupiter.api.Test;
import org.opentripplanner.transit.model.TransitTestEnvironment;
import org.opentripplanner.transit.model.TransitTestEnvironmentBuilder;
import org.opentripplanner.transit.model.TripInput;
import org.opentripplanner.transit.model.site.RegularStop;
import org.opentripplanner.updater.trip.RealtimeTestConstants;
import org.opentripplanner.updater.trip.gtfs.GtfsRtTestHelper;

/**
 * A GTFS-RT update that forbids boarding and alighting everywhere does not cancel the trip: the trip
 * still runs, it just cannot be used. SIRI-ET is the format that reads a cancellation out of the
 * resulting pattern, and that rule must not leak into the GTFS-RT path - see
 * {@link AllStopsSkippedTest} for the same point about an update that skips every stop.
 */
class NonRoutableStopsTest implements RealtimeTestConstants {

  private final TransitTestEnvironmentBuilder ENV_BUILDER = TransitTestEnvironment.of();
  private final RegularStop STOP_A = ENV_BUILDER.stop(STOP_A_ID);
  private final RegularStop STOP_B = ENV_BUILDER.stop(STOP_B_ID);

  private final TripInput TRIP_INPUT = TripInput.of(TRIP_1_ID)
    .addStop(STOP_A, "0:01:00", "0:01:01")
    .addStop(STOP_B, "0:01:10", "0:01:11");

  @Test
  void anUpdateThatForbidsBoardingEverywhereDoesNotCancelTheTrip() {
    var env = ENV_BUILDER.addTrip(TRIP_INPUT).build();
    var rt = GtfsRtTestHelper.of(env);

    var tripUpdate = rt
      .tripUpdateScheduled(TRIP_1_ID)
      .addStopTime(STOP_A_ID, "0:01:01", DropOffPickupType.NONE)
      .addStopTime(STOP_B_ID, "0:01:11", DropOffPickupType.NONE)
      .build();

    assertSuccess(rt.applyTripUpdate(tripUpdate));

    var tripData = env.tripData(TRIP_1_ID);
    assertThat(tripData.tripTimes().isCanceled()).isFalse();
    assertThat(tripData.showTimetable()).isEqualTo("U | A 0:01:01 0:01:01 | B 0:01:11 0:01:11");
  }
}
