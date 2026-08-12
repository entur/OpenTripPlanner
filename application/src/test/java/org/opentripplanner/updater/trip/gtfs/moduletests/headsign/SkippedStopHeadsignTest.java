package org.opentripplanner.updater.trip.gtfs.moduletests.headsign;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opentripplanner.updater.spi.UpdateResultAssertions.assertSuccess;

import org.junit.jupiter.api.Test;
import org.opentripplanner.core.model.i18n.I18NString;
import org.opentripplanner.transit.model.TransitTestEnvironment;
import org.opentripplanner.transit.model.TransitTestEnvironmentBuilder;
import org.opentripplanner.transit.model.TripInput;
import org.opentripplanner.transit.model.site.RegularStop;
import org.opentripplanner.updater.trip.RealtimeTestConstants;
import org.opentripplanner.updater.trip.gtfs.GtfsRtTestHelper;

/**
 * A GTFS-RT {@code SKIPPED} call still displays the headsign it reports, even though it reports no
 * times.
 */
class SkippedStopHeadsignTest implements RealtimeTestConstants {

  private final TransitTestEnvironmentBuilder envBuilder = TransitTestEnvironment.of();
  private final RegularStop STOP_A = envBuilder.stop(STOP_A_ID);
  private final RegularStop STOP_B = envBuilder.stop(STOP_B_ID);
  private final RegularStop STOP_C = envBuilder.stop(STOP_C_ID);

  private final TransitTestEnvironment env = envBuilder
    .addTrip(
      TripInput.of(TRIP_2_ID)
        .withHeadsign(I18NString.of("Scheduled Headsign"))
        .addStop(STOP_A, "0:01:00", "0:01:01")
        .addStop(STOP_B, "0:01:10", "0:01:11")
        .addStop(STOP_C, "0:01:20", "0:01:21")
    )
    .build();

  private final GtfsRtTestHelper gtfsRt = GtfsRtTestHelper.of(env);

  @Test
  void skippedStopKeepsTheHeadsignItReports() {
    var tripUpdate = gtfsRt
      .tripUpdateScheduled(TRIP_2_ID)
      .addDelayedStopTime(0, 0)
      .addSkippedStopWithHeadsign(1, "Not stopping")
      .addDelayedStopTime(2, 90)
      .build();

    assertSuccess(gtfsRt.applyTripUpdate(tripUpdate));

    var tripTimes = env.tripData(TRIP_2_ID).tripTimes();
    assertTrue(tripTimes.isCanceledStop(1));
    assertEquals(I18NString.of("Not stopping"), tripTimes.getHeadsign(1));
    assertEquals(I18NString.of("Scheduled Headsign"), tripTimes.getHeadsign(0));
  }
}
