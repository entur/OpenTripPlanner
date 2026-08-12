package org.opentripplanner.updater.trip.gtfs.moduletests.addition;

import static com.google.transit.realtime.GtfsRealtime.TripDescriptor.ScheduleRelationship.REPLACEMENT;
import static com.google.transit.realtime.GtfsRealtime.TripUpdate.StopTimeUpdate.StopTimeProperties.DropOffPickupType.REGULAR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opentripplanner.updater.spi.UpdateResultAssertions.assertSuccess;
import static org.opentripplanner.updater.trip.UpdateIncrementality.DIFFERENTIAL;

import com.google.transit.realtime.GtfsRealtime;
import org.junit.jupiter.api.Test;
import org.opentripplanner.transit.model.TransitTestEnvironment;
import org.opentripplanner.transit.model.TransitTestEnvironmentBuilder;
import org.opentripplanner.transit.model.TripInput;
import org.opentripplanner.transit.model.site.RegularStop;
import org.opentripplanner.updater.trip.RealtimeTestConstants;
import org.opentripplanner.updater.trip.gtfs.GtfsRtTestHelper;

/**
 * A GTFS-RT replacement resolves its pattern through the shared real-time pattern cache: a repeat
 * of the same message and other trips rerouted over the same stops all share one pattern, and a
 * replacement equal to the scheduled pattern stays on the scheduled pattern.
 */
class ReplacementPatternReuseTest implements RealtimeTestConstants {

  private final TransitTestEnvironmentBuilder envBuilder = TransitTestEnvironment.of();
  private final RegularStop STOP_A = envBuilder.stop(STOP_A_ID);
  private final RegularStop STOP_B = envBuilder.stop(STOP_B_ID);
  private final RegularStop STOP_C = envBuilder.stop(STOP_C_ID);
  private final RegularStop STOP_D = envBuilder.stop(STOP_D_ID);

  private final TransitTestEnvironment env = envBuilder
    .addTrip(
      TripInput.of(TRIP_1_ID).addStop(STOP_A, "8:30:00", "8:30:00").addStop(STOP_B, "8:40:00")
    )
    .addTrip(
      TripInput.of(TRIP_2_ID).addStop(STOP_A, "9:30:00", "9:30:00").addStop(STOP_B, "9:40:00")
    )
    .build();

  private final GtfsRtTestHelper gtfsRt = GtfsRtTestHelper.of(env);

  /** A repeat of the same replacement resolves to the pattern the first message created. */
  @Test
  void aRepeatedReplacementKeepsItsPattern() {
    assertSuccess(gtfsRt.applyTripUpdate(replacementOverNewStops(TRIP_1_ID), DIFFERENTIAL));
    var firstPatternId = env.tripData(TRIP_1_ID).tripPattern().getId();

    assertSuccess(gtfsRt.applyTripUpdate(replacementOverNewStops(TRIP_1_ID), DIFFERENTIAL));

    assertEquals(firstPatternId, env.tripData(TRIP_1_ID).tripPattern().getId());
  }

  /** Two trips replaced over the same stops share one real-time pattern. */
  @Test
  void equalReplacementsShareOnePattern() {
    assertSuccess(gtfsRt.applyTripUpdate(replacementOverNewStops(TRIP_1_ID), DIFFERENTIAL));
    assertSuccess(gtfsRt.applyTripUpdate(replacementOverNewStops(TRIP_2_ID), DIFFERENTIAL));

    assertEquals(
      env.tripData(TRIP_1_ID).tripPattern().getId(),
      env.tripData(TRIP_2_ID).tripPattern().getId()
    );
  }

  /** A replacement whose stops and boarding rules equal the scheduled pattern stays on it. */
  @Test
  void aReplacementEqualToTheScheduledPatternStaysOnIt() {
    var update = gtfsRt
      .tripUpdate(TRIP_1_ID, REPLACEMENT)
      .addStopTime(STOP_A_ID, "8:35", REGULAR)
      .addStopTime(STOP_B_ID, "8:45", REGULAR)
      .build();

    assertSuccess(gtfsRt.applyTripUpdate(update, DIFFERENTIAL));

    var tripData = env.tripData(TRIP_1_ID);
    assertEquals(tripData.scheduledTripPattern().getId(), tripData.tripPattern().getId());
    assertTrue(tripData.tripTimes().isTripPatternModified());
    assertFalse(tripData.tripTimes().isDeleted());
  }

  private GtfsRealtime.TripUpdate replacementOverNewStops(String tripId) {
    return gtfsRt
      .tripUpdate(tripId, REPLACEMENT)
      .addStopTime(STOP_A_ID, "8:35")
      .addStopTime(STOP_C_ID, "8:50")
      .addStopTime(STOP_D_ID, "9:05")
      .build();
  }
}
