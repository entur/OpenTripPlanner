package org.opentripplanner.updater.trip.gtfs.moduletests.fuzzymatching;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opentripplanner.updater.spi.UpdateErrorType.INVALID_INPUT_STRUCTURE;
import static org.opentripplanner.updater.spi.UpdateErrorType.INVALID_STOP_SEQUENCE;
import static org.opentripplanner.updater.spi.UpdateResultAssertions.assertFailure;
import static org.opentripplanner.updater.spi.UpdateResultAssertions.assertSuccess;

import com.google.transit.realtime.GtfsRealtime.TripDescriptor.ScheduleRelationship;
import com.google.transit.realtime.GtfsRealtime.TripUpdate;
import java.time.LocalTime;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.opentripplanner.transit.model.TransitTestEnvironment;
import org.opentripplanner.transit.model.TransitTestEnvironmentBuilder;
import org.opentripplanner.transit.model.TripInput;
import org.opentripplanner.transit.model.network.Route;
import org.opentripplanner.transit.model.site.RegularStop;
import org.opentripplanner.transit.model.timetable.Direction;
import org.opentripplanner.updater.trip.RealtimeTestConstants;
import org.opentripplanner.updater.trip.UnifiedUpdaterOnly;
import org.opentripplanner.updater.trip.gtfs.GtfsRtTestHelper;
import org.opentripplanner.updater.trip.gtfs.TripUpdateBuilder;

/**
 * A feed whose producer cannot supply trip ids is the documented use case for fuzzy trip matching:
 * an update names its trip by route, direction, start time and start date instead of by a
 * {@code trip_id}. All four are required - no subset of them identifies one trip - and an update
 * that carries neither a trip id nor the complete tuple names no trip at all, so it is rejected
 * as structurally invalid. The same update is rejected the same way when fuzzy matching is off,
 * because then only a trip id names a trip.
 */
class TripIdLessMatchingTest implements RealtimeTestConstants {

  private static final String ROUTE_ID = "route-1";
  private static final LocalTime START_TIME = LocalTime.of(10, 0);
  private static final int OUTBOUND_DIRECTION_ID = 0;
  private static final int LAST_STOP_SEQUENCE = 1;
  private static final int DELAY = 60;

  private static final String SCHEDULED = "S | A 10:00 10:00 | B 10:10 10:10";

  private final TransitTestEnvironmentBuilder envBuilder = TransitTestEnvironment.of();
  private final RegularStop stopA = envBuilder.stop(STOP_A_ID);
  private final RegularStop stopB = envBuilder.stop(STOP_B_ID);
  private final RegularStop stopC = envBuilder.stop(STOP_C_ID);
  private final Route route = envBuilder.route(ROUTE_ID);

  private final TripInput trip = TripInput.of(TRIP_1_ID)
    .withRoute(route)
    .addStop(stopA, "10:00")
    .addStop(stopB, "10:10");

  @Test
  void appliesTheDelayToTheTripTheTupleNames() {
    var env = env();
    var rt = GtfsRtTestHelper.ofFuzzyMatching(env);

    var result = rt.applyTripUpdate(
      tripIdLessUpdate(rt).addDelayedStopTime(LAST_STOP_SEQUENCE, DELAY).build()
    );

    assertSuccess(result);
    assertEquals("U | A [ND] 10:00 10:00 | B 10:11 10:11", env.tripData(TRIP_1_ID).showTimetable());
  }

  @Test
  void replacesTheTripTheTupleNames() {
    var env = env();
    var rt = GtfsRtTestHelper.ofFuzzyMatching(env);

    var result = rt.applyTripUpdate(
      tripIdLessUpdate(rt, ScheduleRelationship.REPLACEMENT)
        .addStopTime(STOP_A_ID, "10:30")
        .addStopTime(STOP_B_ID, "10:45")
        .addStopTime(STOP_C_ID, "11:00")
        .build()
    );

    assertSuccess(result);
    var tripData = env.tripData(TRIP_1_ID);
    assertTrue(tripData.tripTimes().isTripPatternModified());
    assertEquals("P U | A 10:30 10:30 | B 10:45 10:45 | C 11:00 11:00", tripData.showTimetable());
  }

  @Test
  void rejectsAnUpdateWithoutARoute() {
    assertRejectedAsInvalid(rt ->
      rt
        .tripUpdateScheduled(TRIP_1_ID)
        .withoutTripId()
        .withStartTime(START_TIME)
        .withDirectionId(OUTBOUND_DIRECTION_ID)
    );
  }

  @Test
  void rejectsAnUpdateWithoutADirection() {
    assertRejectedAsInvalid(rt ->
      rt
        .tripUpdateScheduled(TRIP_1_ID)
        .withoutTripId()
        .withRouteId(ROUTE_ID)
        .withStartTime(START_TIME)
    );
  }

  @Test
  void rejectsAnUpdateWithoutAStartTime() {
    assertRejectedAsInvalid(rt ->
      rt
        .tripUpdateScheduled(TRIP_1_ID)
        .withoutTripId()
        .withRouteId(ROUTE_ID)
        .withDirectionId(OUTBOUND_DIRECTION_ID)
    );
  }

  @Test
  void rejectsAnUpdateWithoutAStartDate() {
    assertRejectedAsInvalid(rt -> tripIdLessUpdate(rt).withoutStartDate());
  }

  /**
   * The tuple is complete but departs at a time no trip on the route leaves at, which only the
   * matcher can discover. The verdict is nevertheless the same as for an incomplete tuple: the
   * message named no trip.
   */
  @Test
  void rejectsAnUpdateWhoseTupleNamesNoTrip() {
    assertRejectedAsInvalid(rt -> tripIdLessUpdate(rt).withStartTime(LocalTime.of(11, 11, 11)));
  }

  /**
   * The call numbering is validated for a message named by its tuple too - and reported against
   * no trip id, which is all the message gave.
   */
  @Test
  void rejectsAnUpdateWithDecreasingStopSequences() {
    var env = env();
    var rt = GtfsRtTestHelper.ofFuzzyMatching(env);

    var result = rt.applyTripUpdate(
      tripIdLessUpdate(rt).addDelayedStopTime(1, DELAY).addDelayedStopTime(0, DELAY).build()
    );

    assertFailure(INVALID_STOP_SEQUENCE, result);
    assertTrue(env.timetableSnapshot().isEmpty());
    assertEquals(SCHEDULED, env.tripData(TRIP_1_ID).showTimetable());
  }

  @Test
  void rejectsAnUpdateWithoutATripIdWhenFuzzyMatchingIsOff() {
    var env = env();
    var rt = GtfsRtTestHelper.of(env);

    var result = rt.applyTripUpdate(
      tripIdLessUpdate(rt).addDelayedStopTime(LAST_STOP_SEQUENCE, DELAY).build()
    );

    assertFailure(INVALID_INPUT_STRUCTURE, result);
    assertTrue(env.timetableSnapshot().isEmpty());
    assertEquals(SCHEDULED, env.tripData(TRIP_1_ID).showTimetable());
  }

  /**
   * A trip that is added is created under the id the message gives it, so a match cannot supply
   * one and both implementations reject the message - for different reasons, pinned below.
   */
  @Test
  void rejectsAnAddedTripWithoutATripId() {
    var env = env();
    var rt = GtfsRtTestHelper.ofFuzzyMatching(env);

    var result = rt.applyTripUpdate(addedTripWithoutTripId(rt));

    assertEquals(1, result.failed());
    assertTrue(env.timetableSnapshot().isEmpty());
  }

  @Test
  @UnifiedUpdaterOnly(
    "Legacy rewrites the descriptor of an ADDED trip to the id of the scheduled trip its tuple " +
      "happens to match and then rejects it as TRIP_ALREADY_EXISTS. A trip that is added is created " +
      "under the id the message gives it - a match cannot supply one - so the unified path rejects " +
      "the message for naming no trip, before any model lookup."
  )
  void rejectsAnAddedTripWithoutATripIdAsStructurallyInvalid() {
    var env = env();
    var rt = GtfsRtTestHelper.ofFuzzyMatching(env);

    var result = rt.applyTripUpdate(addedTripWithoutTripId(rt));

    assertFailure(INVALID_INPUT_STRUCTURE, result);
  }

  private void assertRejectedAsInvalid(Function<GtfsRtTestHelper, TripUpdateBuilder> update) {
    var env = env();
    var rt = GtfsRtTestHelper.ofFuzzyMatching(env);

    var result = rt.applyTripUpdate(
      update.apply(rt).addDelayedStopTime(LAST_STOP_SEQUENCE, DELAY).build()
    );

    assertFailure(INVALID_INPUT_STRUCTURE, result);
    assertTrue(env.timetableSnapshot().isEmpty());
    assertEquals(SCHEDULED, env.tripData(TRIP_1_ID).showTimetable());
  }

  private TransitTestEnvironment env() {
    return envBuilder.addTrip(trip, b -> b.withDirection(Direction.OUTBOUND)).build();
  }

  /** An update naming its trip by the complete tuple: route, direction, start time, start date. */
  private TripUpdateBuilder tripIdLessUpdate(GtfsRtTestHelper rt) {
    return tripIdLessUpdate(rt, ScheduleRelationship.SCHEDULED);
  }

  private TripUpdateBuilder tripIdLessUpdate(
    GtfsRtTestHelper rt,
    ScheduleRelationship relationship
  ) {
    return rt
      .tripUpdate(TRIP_1_ID, relationship)
      .withoutTripId()
      .withRouteId(ROUTE_ID)
      .withStartTime(START_TIME)
      .withDirectionId(OUTBOUND_DIRECTION_ID);
  }

  private TripUpdate addedTripWithoutTripId(GtfsRtTestHelper rt) {
    return tripIdLessUpdate(rt, ScheduleRelationship.ADDED)
      .addStopTime(STOP_A_ID, "10:00")
      .addStopTime(STOP_B_ID, "10:10")
      .build();
  }
}
