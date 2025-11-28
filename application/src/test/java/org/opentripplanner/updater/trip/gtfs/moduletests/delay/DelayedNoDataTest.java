package org.opentripplanner.updater.trip.gtfs.moduletests.delay;

import static org.opentripplanner.updater.spi.UpdateResultAssertions.assertSuccess;

import org.junit.jupiter.api.Test;
import org.opentripplanner.transit.model._data.TransitTestEnvironment;
import org.opentripplanner.transit.model._data.TransitTestEnvironmentBuilder;
import org.opentripplanner.transit.model._data.TripInput;
import org.opentripplanner.transit.model.site.RegularStop;
import org.opentripplanner.updater.trip.GtfsRtTestHelper;
import org.opentripplanner.updater.trip.RealtimeTestConstants;

class DelayedNoDataTest implements RealtimeTestConstants {

  private final TransitTestEnvironmentBuilder envBuilder = TransitTestEnvironment.of();
  private final RegularStop stopA = envBuilder.stop(STOP_A_ID);
  private final RegularStop stopB = envBuilder.stop(STOP_B_ID);
  private final RegularStop stopC = envBuilder.stop(STOP_C_ID);

  @Test
  void singleStopDelay() {
    var tripInput = TripInput.of(TRIP_1_ID)
      .addStop(stopA, "10:00", "10:00")
      .addStop(stopB, "10:10", "10:10")
      .addStop(stopC, "10:20", "10:20");
    var env = envBuilder.addTrip(tripInput).build();
    var rt = GtfsRtTestHelper.of(env);

    var tripUpdate = rt
      .tripUpdateScheduled(TRIP_1_ID)
      .addNoDataStop(0)
      .addNoDataStop(1)
      .addStopTime(2, "10:09")
      .build();

    var result = rt.applyTripUpdate(tripUpdate);

    assertSuccess(result);
  }
}
