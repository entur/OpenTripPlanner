package org.opentripplanner.updater.trip.gtfs.moduletests.addition;

import static com.google.common.truth.Truth.assertThat;
import static com.google.transit.realtime.GtfsRealtime.TripDescriptor.ScheduleRelationship.ADDED;
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
 * A GTFS-RT trip is added by a message that carries its whole stop list, and nothing says a later
 * message for the same trip has to carry the same one. Each message therefore rebuilds the trip and
 * the pattern it runs on from the calls it carries.
 */
class ReAddedTripPatternTest implements RealtimeTestConstants {

  private final TransitTestEnvironmentBuilder envBuilder = TransitTestEnvironment.of();
  private final RegularStop STOP_A = envBuilder.stop(STOP_A_ID);
  private final RegularStop STOP_B = envBuilder.stop(STOP_B_ID);
  private final RegularStop STOP_C = envBuilder.stop(STOP_C_ID);

  private final TransitTestEnvironment env = envBuilder
    .addTrip(
      // just to set the schedule period
      TripInput.of(TRIP_1_ID)
        .withServiceDates(
          envBuilder.defaultServiceDate().minusDays(1),
          envBuilder.defaultServiceDate().plusDays(1)
        )
        .addStop(STOP_A, "12:00", "12:00")
        .addStop(STOP_B, "12:10", "12:10")
        .addStop(STOP_C, "12:20", "12:20")
    )
    .addStops(STOP_A_ID, STOP_B_ID, STOP_C_ID, STOP_D_ID)
    .build();

  private final GtfsRtTestHelper gtfsRt = GtfsRtTestHelper.of(env);

  @Test
  void aReAddedTripGainsAStop() {
    addTheTrip();

    var update = gtfsRt
      .tripUpdate(ADDED_TRIP_ID, ADDED)
      .addStopTime(STOP_A_ID, "01:00")
      .addStopTime(STOP_B_ID, "01:10")
      .addStopTime(STOP_C_ID, "01:25")
      .addStopTime(STOP_D_ID, "01:40")
      .build();

    assertSuccess(gtfsRt.applyTripUpdate(update, DIFFERENTIAL));

    assertStops(STOP_A_ID, STOP_B_ID, STOP_C_ID, STOP_D_ID);
    assertThat(env.tripData(ADDED_TRIP_ID).showTimetable()).isEqualTo(
      "A U | A 1:00 1:00 | B 1:10 1:10 | C 1:25 1:25 | D 1:40 1:40"
    );
  }

  @Test
  void aReAddedTripLosesAStop() {
    addTheTrip();

    var update = gtfsRt
      .tripUpdate(ADDED_TRIP_ID, ADDED)
      .addStopTime(STOP_A_ID, "01:00")
      .addStopTime(STOP_B_ID, "01:10")
      .build();

    assertSuccess(gtfsRt.applyTripUpdate(update, DIFFERENTIAL));

    assertStops(STOP_A_ID, STOP_B_ID);
    assertThat(env.tripData(ADDED_TRIP_ID).showTimetable()).isEqualTo(
      "A U | A 1:00 1:00 | B 1:10 1:10"
    );
  }

  /**
   * The stop count is unchanged, so a stop-count invariant would not catch this one: the times of
   * the second message belong to a stop list the added trip no longer runs.
   */
  @Test
  void aReAddedTripChangesAStop() {
    addTheTrip();

    var update = gtfsRt
      .tripUpdate(ADDED_TRIP_ID, ADDED)
      .addStopTime(STOP_A_ID, "01:00")
      .addStopTime(STOP_B_ID, "01:10")
      .addStopTime(STOP_D_ID, "01:25")
      .build();

    assertSuccess(gtfsRt.applyTripUpdate(update, DIFFERENTIAL));

    assertStops(STOP_A_ID, STOP_B_ID, STOP_D_ID);
    assertThat(env.tripData(ADDED_TRIP_ID).showTimetable()).isEqualTo(
      "A U | A 1:00 1:00 | B 1:10 1:10 | D 1:25 1:25"
    );
  }

  private void addTheTrip() {
    var creation = gtfsRt
      .tripUpdate(ADDED_TRIP_ID, ADDED)
      .addStopTime(STOP_A_ID, "00:30")
      .addStopTime(STOP_B_ID, "00:40")
      .addStopTime(STOP_C_ID, "00:55")
      .build();

    assertSuccess(gtfsRt.applyTripUpdate(creation, DIFFERENTIAL));
    assertStops(STOP_A_ID, STOP_B_ID, STOP_C_ID);
  }

  private void assertStops(String... expectedStopIds) {
    assertThat(
      env
        .tripData(ADDED_TRIP_ID)
        .tripPattern()
        .getStops()
        .stream()
        .map(stop -> stop.getId().getId())
        .toList()
    )
      .containsExactlyElementsIn(expectedStopIds)
      .inOrder();
  }
}
