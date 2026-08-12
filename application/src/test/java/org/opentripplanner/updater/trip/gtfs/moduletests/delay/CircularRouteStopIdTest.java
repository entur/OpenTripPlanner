package org.opentripplanner.updater.trip.gtfs.moduletests.delay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.opentripplanner.updater.spi.UpdateErrorType.INVALID_STOP_REFERENCE;
import static org.opentripplanner.updater.spi.UpdateResultAssertions.assertFailure;
import static org.opentripplanner.updater.spi.UpdateResultAssertions.assertSuccess;

import org.junit.jupiter.api.Test;
import org.opentripplanner.transit.model.TransitTestEnvironment;
import org.opentripplanner.transit.model.TransitTestEnvironmentBuilder;
import org.opentripplanner.transit.model.TripInput;
import org.opentripplanner.transit.model.site.RegularStop;
import org.opentripplanner.updater.trip.LegacyUpdaterOnly;
import org.opentripplanner.updater.trip.RealtimeTestConstants;
import org.opentripplanner.updater.trip.UnifiedUpdaterOnly;
import org.opentripplanner.updater.trip.gtfs.GtfsRtTestHelper;

/**
 * A trip that calls at the same stop twice, updated by stop id only.
 *
 * <p>GTFS-RT requires such a trip to number its calls with their stop sequence, precisely so that
 * an update can say which of the two visits it is for. The unified implementation therefore
 * rejects a stop id that names the repeated stop, while legacy guesses a visit - the divergence is
 * pinned by the companion tests below.
 */
class CircularRouteStopIdTest implements RealtimeTestConstants {

  private static final String LEGACY_GUESSES =
    "The legacy implementation guesses which of the visits to the repeated stop is meant - see the companion test.";
  private static final String UNIFIED_REJECTS =
    "The unified implementation rejects a stop id that does not identify a single call - see the companion test.";

  private final TransitTestEnvironmentBuilder builder = TransitTestEnvironment.of();
  private final RegularStop stopA = builder.stop(STOP_A_ID);
  private final RegularStop stopB = builder.stop(STOP_B_ID);
  private final TripInput tripInput = TripInput.of(TRIP_1_ID)
    .addStop(stopA, "10:00", "10:00")
    .addStop(stopB, "10:10", "10:10")
    .addStop(stopA, "10:20", "10:20");

  /** A stop sequence says which of the visits to the repeated stop each update is for. */
  @Test
  void stopSequences() {
    var env = builder.addTrip(tripInput).build();
    var rt = GtfsRtTestHelper.of(env);
    var update = rt
      .tripUpdateScheduled(TRIP_1_ID)
      .addStopTime(STOP_A_ID, 0, "10:01")
      .addStopTime(STOP_B_ID, 1, "10:21")
      .addStopTime(STOP_A_ID, 2, "10:31")
      .build();

    assertSuccess(rt.applyTripUpdate(update));

    assertEquals(
      "U | A 10:01 10:01 | B 10:21 10:21 | A 10:31 10:31",
      env.tripData(TRIP_1_ID).showTimetable()
    );
  }

  /** A stop the trip calls at once is identified by its stop id, repeated stops elsewhere or not. */
  @Test
  void onlyStopIdOfUnrepeatedStop() {
    var env = builder.addTrip(tripInput).build();
    var rt = GtfsRtTestHelper.of(env);
    var update = rt.tripUpdateScheduled(TRIP_1_ID).addStopTime(STOP_B_ID, "10:21").build();

    assertSuccess(rt.applyTripUpdate(update));

    assertEquals(
      "U | A [ND] 10:00 10:00 | B 10:21 10:21 | A 10:31 10:31",
      env.tripData(TRIP_1_ID).showTimetable()
    );
  }

  @Test
  @UnifiedUpdaterOnly(LEGACY_GUESSES)
  void onlyStopIdsRejected() {
    var env = builder.addTrip(tripInput).build();
    var rt = GtfsRtTestHelper.of(env);
    var update = rt
      .tripUpdateScheduled(TRIP_1_ID)
      .addStopTime(STOP_A_ID, "10:01")
      .addStopTime(STOP_B_ID, "10:21")
      .addStopTime(STOP_A_ID, "10:31")
      .build();

    assertFailure(INVALID_STOP_REFERENCE, rt.applyTripUpdate(update));

    assertEquals(
      "S | A 10:00 10:00 | B 10:10 10:10 | A 10:20 10:20",
      env.tripData(TRIP_1_ID).showTimetable()
    );
  }

  /** The repeated stop is ambiguous whether or not the beginning of the trip is updated. */
  @Test
  @UnifiedUpdaterOnly(LEGACY_GUESSES)
  void onlyStopIdsFromMiddleOfTripRejected() {
    var env = builder.addTrip(tripInput).build();
    var rt = GtfsRtTestHelper.of(env);
    var update = rt
      .tripUpdateScheduled(TRIP_1_ID)
      .addStopTime(STOP_B_ID, "10:21")
      .addStopTime(STOP_A_ID, "10:31")
      .build();

    assertFailure(INVALID_STOP_REFERENCE, rt.applyTripUpdate(update));

    assertEquals(
      "S | A 10:00 10:00 | B 10:10 10:10 | A 10:20 10:20",
      env.tripData(TRIP_1_ID).showTimetable()
    );
  }

  @Test
  @LegacyUpdaterOnly(UNIFIED_REJECTS)
  void onlyStopIds() {
    var env = builder.addTrip(tripInput).build();
    var rt = GtfsRtTestHelper.of(env);
    var update = rt
      .tripUpdateScheduled(TRIP_1_ID)
      .addStopTime(STOP_A_ID, "10:01")
      .addStopTime(STOP_B_ID, "10:21")
      .addStopTime(STOP_A_ID, "10:31")
      .build();

    assertSuccess(rt.applyTripUpdate(update));

    assertEquals(
      "U | A 10:01 10:01 | B 10:21 10:21 | A 10:31 10:31",
      env.tripData(TRIP_1_ID).showTimetable()
    );
  }

  @Test
  @LegacyUpdaterOnly(UNIFIED_REJECTS)
  void missingStopAtBeginning() {
    var env = builder.addTrip(tripInput).build();
    var rt = GtfsRtTestHelper.of(env);
    var update = rt
      .tripUpdateScheduled(TRIP_1_ID)
      .addStopTime(STOP_B_ID, "10:21")
      .addStopTime(STOP_A_ID, "10:31")
      .build();

    assertSuccess(rt.applyTripUpdate(update));

    assertEquals(
      "U | A [ND] 10:00 10:00 | B 10:21 10:21 | A 10:31 10:31",
      env.tripData(TRIP_1_ID).showTimetable()
    );
  }

  @Test
  @LegacyUpdaterOnly(UNIFIED_REJECTS)
  void missingStopAtEnd() {
    var env = builder.addTrip(tripInput).build();
    var rt = GtfsRtTestHelper.of(env);
    var update = rt
      .tripUpdateScheduled(TRIP_1_ID)
      .addStopTime(STOP_A_ID, "10:11")
      .addStopTime(STOP_B_ID, "10:21")
      .build();

    assertSuccess(rt.applyTripUpdate(update));

    assertEquals(
      "U | A 10:11 10:11 | B 10:21 10:21 | A 10:31 10:31",
      env.tripData(TRIP_1_ID).showTimetable()
    );
  }
}
