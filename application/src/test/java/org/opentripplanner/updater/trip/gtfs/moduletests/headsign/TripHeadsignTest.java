package org.opentripplanner.updater.trip.gtfs.moduletests.headsign;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
 * The trip headsign from GTFS-RT {@code TripProperties} should be propagated to the real-time trip
 * times of a plain scheduled update, not only when a trip is added or replaced.
 */
class TripHeadsignTest implements RealtimeTestConstants {

  private final TransitTestEnvironmentBuilder envBuilder = TransitTestEnvironment.of();
  private final RegularStop STOP_A = envBuilder.stop(STOP_A_ID);
  private final RegularStop STOP_B = envBuilder.stop(STOP_B_ID);

  private final TransitTestEnvironment env = envBuilder
    .addTrip(
      TripInput.of(TRIP_1_ID)
        .withHeadsign(I18NString.of("Original Headsign"))
        .addStop(STOP_A, "12:00", "12:00")
        .addStop(STOP_B, "12:10", "12:10")
    )
    .build();
  private final GtfsRtTestHelper gtfsRt = GtfsRtTestHelper.of(env);

  @Test
  void tripHeadsignIsSetOnScheduledTripUpdate() {
    var tripUpdate = gtfsRt
      .tripUpdateScheduled(TRIP_1_ID)
      .withTripHeadsign("Updated Headsign")
      .addDelayedStopTime(0, 0)
      .addDelayedStopTime(1, 60)
      .build();

    assertSuccess(gtfsRt.applyTripUpdate(tripUpdate));

    assertEquals(
      I18NString.of("Updated Headsign"),
      env.tripData(TRIP_1_ID).tripTimes().getTripHeadsign()
    );
  }

  /**
   * An update that says nothing about the headsign must not clear the headsign of the trip it
   * updates.
   */
  @Test
  void tripHeadsignIsKeptWhenTheMessageOmitsIt() {
    var tripUpdate = gtfsRt
      .tripUpdateScheduled(TRIP_1_ID)
      .addDelayedStopTime(0, 0)
      .addDelayedStopTime(1, 60)
      .build();

    assertSuccess(gtfsRt.applyTripUpdate(tripUpdate));

    assertEquals(
      I18NString.of("Original Headsign"),
      env.tripData(TRIP_1_ID).tripTimes().getTripHeadsign()
    );
  }
}
