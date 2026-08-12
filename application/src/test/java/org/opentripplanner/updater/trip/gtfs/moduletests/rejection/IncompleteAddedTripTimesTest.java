package org.opentripplanner.updater.trip.gtfs.moduletests.rejection;

import static com.google.transit.realtime.GtfsRealtime.TripDescriptor.ScheduleRelationship.ADDED;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opentripplanner.updater.spi.UpdateErrorType.INVALID_ARRIVAL_TIME;
import static org.opentripplanner.updater.spi.UpdateErrorType.INVALID_DEPARTURE_TIME;
import static org.opentripplanner.updater.spi.UpdateErrorType.NEGATIVE_HOP_TIME;
import static org.opentripplanner.updater.spi.UpdateResultAssertions.assertFailure;
import static org.opentripplanner.updater.spi.UpdateResultAssertions.assertSuccess;

import com.google.transit.realtime.GtfsRealtime.TripUpdate;
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
 * A NEW, ADDED or REPLACEMENT trip brings its own schedule, so every call must state a usable
 * time: with no scheduled timetable to fall back on, a timeless call cannot be placed in the
 * timetable. The unified implementation rejects the entity at the call in question instead of
 * inventing a time for it. Legacy leaves the time unset, which its timetable validation catches
 * as a negative hop for every stop but the first - a timeless first stop it publishes with the
 * unset value.
 */
class IncompleteAddedTripTimesTest implements RealtimeTestConstants {

  private static final String LEGACY_DIFFERS =
    "The legacy implementation leaves the time unset and relies on downstream timetable " +
    "validation - see the companion test.";
  private static final String UNIFIED_DIFFERS =
    "The unified implementation rejects the timeless call directly - see the companion test.";

  private final TransitTestEnvironmentBuilder envBuilder = TransitTestEnvironment.of();
  private final RegularStop stopA = envBuilder.stop(STOP_A_ID);
  private final RegularStop stopB = envBuilder.stop(STOP_B_ID);
  private final RegularStop stopC = envBuilder.stop(STOP_C_ID);
  private final TransitTestEnvironment env = envBuilder
    .addTrip(
      TripInput.of(TRIP_1_ID)
        .addStop(stopA, "10:00", "10:00")
        .addStop(stopB, "10:10", "10:10")
        .addStop(stopC, "10:20", "10:20")
    )
    .build();
  private final GtfsRtTestHelper gtfsRt = GtfsRtTestHelper.of(env);

  /* Timeless middle stop */

  @UnifiedUpdaterOnly(LEGACY_DIFFERS)
  @Test
  void addedTripWithATimelessMiddleStopIsRejected() {
    assertFailure(INVALID_ARRIVAL_TIME, gtfsRt.applyTripUpdate(timelessMiddleStop()));
    assertTrue(env.timetableSnapshot().isEmpty());
  }

  @LegacyUpdaterOnly(UNIFIED_DIFFERS)
  @Test
  void addedTripWithATimelessMiddleStopIsRejectedAsANegativeHop() {
    assertFailure(NEGATIVE_HOP_TIME, gtfsRt.applyTripUpdate(timelessMiddleStop()));
    assertTrue(env.timetableSnapshot().isEmpty());
  }

  /* Timeless last stop */

  @UnifiedUpdaterOnly(LEGACY_DIFFERS)
  @Test
  void addedTripWithATimelessLastStopIsRejected() {
    assertFailure(INVALID_ARRIVAL_TIME, gtfsRt.applyTripUpdate(timelessLastStop()));
    assertTrue(env.timetableSnapshot().isEmpty());
  }

  @LegacyUpdaterOnly(UNIFIED_DIFFERS)
  @Test
  void addedTripWithATimelessLastStopIsRejectedAsANegativeHop() {
    assertFailure(NEGATIVE_HOP_TIME, gtfsRt.applyTripUpdate(timelessLastStop()));
    assertTrue(env.timetableSnapshot().isEmpty());
  }

  /* Timeless first stop */

  @UnifiedUpdaterOnly(
    "The legacy timetable validation does not reach a timeless first stop - it publishes the " +
      "trip with the time left unset. See the companion test."
  )
  @Test
  void addedTripWithATimelessFirstStopIsRejected() {
    assertFailure(INVALID_DEPARTURE_TIME, gtfsRt.applyTripUpdate(timelessFirstStop()));
    assertTrue(env.timetableSnapshot().isEmpty());
  }

  @LegacyUpdaterOnly(
    "The unified implementation rejects the timeless call directly. Publishing the unset value " +
      "is a legacy defect this test records, not a behaviour to preserve."
  )
  @Test
  void addedTripWithATimelessFirstStopIsPublishedWithTheUnsetValue() {
    assertSuccess(gtfsRt.applyTripUpdate(timelessFirstStop()));
    assertFalse(env.timetableSnapshot().isEmpty());
  }

  private TripUpdate timelessMiddleStop() {
    return gtfsRt
      .tripUpdate(ADDED_TRIP_ID, ADDED)
      .addStopTime(STOP_A_ID, "00:30")
      .addStopTimeWithArrivalAndDeparture(STOP_B_ID, null, null)
      .addStopTime(STOP_C_ID, "00:55")
      .build();
  }

  private TripUpdate timelessLastStop() {
    return gtfsRt
      .tripUpdate(ADDED_TRIP_ID, ADDED)
      .addStopTime(STOP_A_ID, "00:30")
      .addStopTime(STOP_B_ID, "00:40")
      .addStopTimeWithArrivalAndDeparture(STOP_C_ID, null, null)
      .build();
  }

  private TripUpdate timelessFirstStop() {
    return gtfsRt
      .tripUpdate(ADDED_TRIP_ID, ADDED)
      .addStopTimeWithArrivalAndDeparture(STOP_A_ID, null, null)
      .addStopTime(STOP_B_ID, "00:40")
      .addStopTime(STOP_C_ID, "00:55")
      .build();
  }
}
