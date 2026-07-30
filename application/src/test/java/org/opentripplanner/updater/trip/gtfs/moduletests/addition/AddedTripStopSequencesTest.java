package org.opentripplanner.updater.trip.gtfs.moduletests.addition;

import static com.google.transit.realtime.GtfsRealtime.TripDescriptor.ScheduleRelationship.ADDED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.opentripplanner.updater.spi.UpdateResultAssertions.assertSuccess;

import java.util.OptionalInt;
import org.junit.jupiter.api.Test;
import org.opentripplanner.transit.model.TransitTestEnvironment;
import org.opentripplanner.transit.model.TransitTestEnvironmentBuilder;
import org.opentripplanner.transit.model.TripInput;
import org.opentripplanner.transit.model.site.RegularStop;
import org.opentripplanner.updater.trip.RealtimeTestConstants;
import org.opentripplanner.updater.trip.gtfs.GtfsRtTestHelper;

/**
 * A trip added by a GTFS-RT message keeps the numbering of the message: the calls are numbered with
 * the {@code stop_sequence} they were sent with, not with their position in the new pattern.
 */
class AddedTripStopSequencesTest implements RealtimeTestConstants {

  private final TransitTestEnvironmentBuilder envBuilder = TransitTestEnvironment.of();
  private final RegularStop stopA = envBuilder.stop(STOP_A_ID);
  private final RegularStop stopB = envBuilder.stop(STOP_B_ID);
  private final RegularStop stopC = envBuilder.stop(STOP_C_ID);

  private final TransitTestEnvironment env = envBuilder
    .addTrip(
      // just to set the schedule period
      TripInput.of(TRIP_1_ID).addStop(stopA, "12:00", "12:00").addStop(stopB, "12:10", "12:10")
    )
    .addStops(STOP_A_ID, STOP_B_ID, STOP_C_ID)
    .build();
  private final GtfsRtTestHelper gtfsRt = GtfsRtTestHelper.of(env);

  @Test
  void addedTripKeepsTheStopSequencesOfTheMessage() {
    var update = gtfsRt
      .tripUpdate(ADDED_TRIP_ID, ADDED)
      .addStopTime(STOP_A_ID, 1, "00:30")
      .addStopTime(STOP_B_ID, 2, "00:40")
      .addStopTime(STOP_C_ID, 3, "00:55")
      .build();

    assertSuccess(gtfsRt.applyTripUpdate(update));

    var tripTimes = env.tripData(ADDED_TRIP_ID).tripTimes();
    assertEquals(OptionalInt.of(0), tripTimes.stopPositionForGtfsSequence(1));
    assertEquals(OptionalInt.of(1), tripTimes.stopPositionForGtfsSequence(2));
    assertEquals(OptionalInt.of(2), tripTimes.stopPositionForGtfsSequence(3));
    assertEquals(
      OptionalInt.empty(),
      tripTimes.stopPositionForGtfsSequence(0),
      "the message numbered no call 0"
    );
  }
}
