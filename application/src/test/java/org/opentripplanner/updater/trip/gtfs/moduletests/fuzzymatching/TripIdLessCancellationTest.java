package org.opentripplanner.updater.trip.gtfs.moduletests.fuzzymatching;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opentripplanner.updater.spi.UpdateErrorType.INVALID_INPUT_STRUCTURE;
import static org.opentripplanner.updater.spi.UpdateErrorType.NO_TRIP_FOR_CANCELLATION_FOUND;
import static org.opentripplanner.updater.spi.UpdateResultAssertions.assertFailure;
import static org.opentripplanner.updater.spi.UpdateResultAssertions.assertSuccess;

import com.google.transit.realtime.GtfsRealtime.TripDescriptor.ScheduleRelationship;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import org.opentripplanner.transit.model.TransitTestEnvironment;
import org.opentripplanner.transit.model.TransitTestEnvironmentBuilder;
import org.opentripplanner.transit.model.TripInput;
import org.opentripplanner.transit.model.network.Route;
import org.opentripplanner.transit.model.site.RegularStop;
import org.opentripplanner.transit.model.timetable.Direction;
import org.opentripplanner.updater.trip.RealtimeTestConstants;
import org.opentripplanner.updater.trip.gtfs.GtfsRtTestHelper;
import org.opentripplanner.updater.trip.gtfs.TripUpdateBuilder;

/**
 * A feed that identifies trips by route, direction, start time and start date instead of by
 * {@code trip_id} cancels and deletes them the same way, so CANCELED and DELETED participate in
 * fuzzy trip matching like every other schedule relationship: the matcher runs on the raw
 * identifiers, before the message's intent is considered.
 */
class TripIdLessCancellationTest implements RealtimeTestConstants {

  private static final String ROUTE_ID = "route-1";
  private static final String UNKNOWN_TRIP_ID = "not-in-the-schedule";
  private static final LocalTime START_TIME = LocalTime.of(10, 0);
  private static final int OUTBOUND_DIRECTION_ID = 0;

  private static final String SCHEDULED = "S | A 10:00 10:00 | B 10:10 10:10";

  private final TransitTestEnvironmentBuilder envBuilder = TransitTestEnvironment.of();
  private final RegularStop stopA = envBuilder.stop(STOP_A_ID);
  private final RegularStop stopB = envBuilder.stop(STOP_B_ID);
  private final Route route = envBuilder.route(ROUTE_ID);

  private final TripInput trip = TripInput.of(TRIP_1_ID)
    .withRoute(route)
    .addStop(stopA, "10:00")
    .addStop(stopB, "10:10");

  @Test
  void cancelsTheTripTheTupleNames() {
    var env = env();
    var rt = GtfsRtTestHelper.ofFuzzyMatching(env);

    var result = rt.applyTripUpdate(
      fuzzyUpdate(rt, ScheduleRelationship.CANCELED).withoutTripId().build()
    );

    assertSuccess(result);
    var tripTimes = env.tripData(TRIP_1_ID).tripTimes();
    assertTrue(tripTimes.isCanceled());
    assertFalse(tripTimes.isDeleted());
  }

  @Test
  void deletesTheTripTheTupleNames() {
    var env = env();
    var rt = GtfsRtTestHelper.ofFuzzyMatching(env);

    var result = rt.applyTripUpdate(
      fuzzyUpdate(rt, ScheduleRelationship.DELETED).withoutTripId().build()
    );

    assertSuccess(result);
    var tripTimes = env.tripData(TRIP_1_ID).tripTimes();
    assertTrue(tripTimes.isDeleted());
    assertFalse(tripTimes.isCanceled());
  }

  /**
   * The trip id names no trip, but the tuple does - and the tuple wins, exactly as it does for a
   * delay update carrying the same identifiers.
   */
  @Test
  void cancelsTheTripMatchedForAnUnknownTripId() {
    var env = env();
    var rt = GtfsRtTestHelper.ofFuzzyMatching(env);

    var result = rt.applyTripUpdate(fuzzyUpdate(rt, ScheduleRelationship.CANCELED).build());

    assertSuccess(result);
    assertTrue(env.tripData(TRIP_1_ID).tripTimes().isCanceled());
  }

  /**
   * The tuple is complete but names no trip, so the message identifies nothing at all and is
   * rejected the way any message naming no trip is.
   */
  @Test
  void rejectsACancellationWhoseTupleNamesNoTrip() {
    var env = env();
    var rt = GtfsRtTestHelper.ofFuzzyMatching(env);

    var result = rt.applyTripUpdate(
      fuzzyUpdate(rt, ScheduleRelationship.CANCELED)
        .withoutTripId()
        .withStartTime(LocalTime.of(11, 11, 11))
        .build()
    );

    assertFailure(INVALID_INPUT_STRUCTURE, result);
    assertTrue(env.timetableSnapshot().isEmpty());
    assertEquals(SCHEDULED, env.tripData(TRIP_1_ID).showTimetable());
  }

  /**
   * An unknown trip id without the tuple leaves the matcher nothing to work with, and the
   * cancellation reports its own failure: no trip to cancel.
   */
  @Test
  void rejectsACancellationOfAnUnknownTripWithoutMatchKeys() {
    var env = env();
    var rt = GtfsRtTestHelper.ofFuzzyMatching(env);

    var result = rt.applyTripUpdate(
      rt.tripUpdate(UNKNOWN_TRIP_ID, ScheduleRelationship.CANCELED).build()
    );

    assertFailure(NO_TRIP_FOR_CANCELLATION_FOUND, result);
    assertTrue(env.timetableSnapshot().isEmpty());
  }

  private TransitTestEnvironment env() {
    return envBuilder.addTrip(trip, b -> b.withDirection(Direction.OUTBOUND)).build();
  }

  /**
   * A removal carrying everything a match needs: route, direction, start time and - from the
   * builder's default - the start date. The trip id is the builder's default unknown one until a
   * test drops it.
   */
  private TripUpdateBuilder fuzzyUpdate(GtfsRtTestHelper rt, ScheduleRelationship relationship) {
    return rt
      .tripUpdate(UNKNOWN_TRIP_ID, relationship)
      .withRouteId(ROUTE_ID)
      .withStartTime(START_TIME)
      .withDirectionId(OUTBOUND_DIRECTION_ID);
  }
}
