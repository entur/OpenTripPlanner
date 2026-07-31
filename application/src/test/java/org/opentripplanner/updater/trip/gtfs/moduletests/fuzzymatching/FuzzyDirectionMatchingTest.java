package org.opentripplanner.updater.trip.gtfs.moduletests.fuzzymatching;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opentripplanner.updater.spi.UpdateErrorType.TRIP_NOT_FOUND;
import static org.opentripplanner.updater.spi.UpdateResultAssertions.assertFailure;
import static org.opentripplanner.updater.spi.UpdateResultAssertions.assertSuccess;

import com.google.transit.realtime.GtfsRealtime.TripUpdate;
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
 * An update whose trip id is not in the schedule is matched to a scheduled trip by route, direction
 * and start time. All three are required. A line worked from both ends at once has an outbound and
 * an inbound trip leaving at the same time, and the match only compares the departure time at the
 * first stop without looking at which stop that is, so an update that says nothing about its
 * direction has two equally good candidates. Matching one of them would apply the update to a trip
 * running the other way, so no match is reported instead.
 */
class FuzzyDirectionMatchingTest implements RealtimeTestConstants {

  private static final String ROUTE_ID = "route-1";
  private static final String UNKNOWN_TRIP_ID = "not-in-the-schedule";
  private static final LocalTime START_TIME = LocalTime.of(10, 0);
  private static final int OUTBOUND_DIRECTION_ID = 0;
  private static final int INBOUND_DIRECTION_ID = 1;
  private static final int LAST_STOP_SEQUENCE = 1;
  private static final int DELAY = 60;

  private static final String SCHEDULED_OUTBOUND = "S | A 10:00 10:00 | B 10:10 10:10";
  private static final String SCHEDULED_INBOUND = "S | B 10:00 10:00 | A 10:10 10:10";

  private final TransitTestEnvironmentBuilder envBuilder = TransitTestEnvironment.of();
  private final RegularStop stopA = envBuilder.stop(STOP_A_ID);
  private final RegularStop stopB = envBuilder.stop(STOP_B_ID);
  private final Route route = envBuilder.route(ROUTE_ID);

  /** Outbound, leaving A at 10:00. */
  private final TripInput outboundTrip = TripInput.of(TRIP_1_ID)
    .withRoute(route)
    .addStop(stopA, "10:00")
    .addStop(stopB, "10:10");

  /** Inbound, leaving B at the same 10:00. */
  private final TripInput inboundTrip = TripInput.of(TRIP_2_ID)
    .withRoute(route)
    .addStop(stopB, "10:00")
    .addStop(stopA, "10:10");

  @Test
  void matchesTheTripGoingInTheReportedDirection() {
    var env = envWithBothDirections();
    var rt = GtfsRtTestHelper.ofFuzzyMatching(env);

    var result = rt.applyTripUpdate(delayedUpdate(rt, OUTBOUND_DIRECTION_ID));

    assertSuccess(result);
    assertEquals("U | A [ND] 10:00 10:00 | B 10:11 10:11", env.tripData(TRIP_1_ID).showTimetable());
    assertEquals(SCHEDULED_INBOUND, env.tripData(TRIP_2_ID).showTimetable());
  }

  @Test
  void matchesTheTripGoingInTheOtherReportedDirection() {
    var env = envWithBothDirections();
    var rt = GtfsRtTestHelper.ofFuzzyMatching(env);

    var result = rt.applyTripUpdate(delayedUpdate(rt, INBOUND_DIRECTION_ID));

    assertSuccess(result);
    assertEquals("U | B [ND] 10:00 10:00 | A 10:11 10:11", env.tripData(TRIP_2_ID).showTimetable());
    assertEquals(SCHEDULED_OUTBOUND, env.tripData(TRIP_1_ID).showTimetable());
  }

  /**
   * Without a direction the update fits both trips, and neither of them may be updated.
   */
  @Test
  void doesNotMatchWithoutADirection() {
    var env = envWithBothDirections();
    var rt = GtfsRtTestHelper.ofFuzzyMatching(env);

    var result = rt.applyTripUpdate(
      fuzzyUpdate(rt).addDelayedStopTime(LAST_STOP_SEQUENCE, DELAY).build()
    );

    // The failed match has no verdict of its own: the update is rejected for what it said, a trip
    // id no schedule has.
    assertFailure(TRIP_NOT_FOUND, result);
    assertTrue(env.timetableSnapshot().isEmpty());
    assertEquals(SCHEDULED_OUTBOUND, env.tripData(TRIP_1_ID).showTimetable());
    assertEquals(SCHEDULED_INBOUND, env.tripData(TRIP_2_ID).showTimetable());
  }

  /**
   * The direction is a condition on the match, not a preference: a trip going the other way is not
   * matched just because it is the only candidate left.
   */
  @Test
  void doesNotMatchATripGoingTheOtherWay() {
    var env = envBuilder.addTrip(outboundTrip, b -> b.withDirection(Direction.OUTBOUND)).build();
    var rt = GtfsRtTestHelper.ofFuzzyMatching(env);

    var result = rt.applyTripUpdate(delayedUpdate(rt, INBOUND_DIRECTION_ID));

    assertFailure(TRIP_NOT_FOUND, result);
    assertTrue(env.timetableSnapshot().isEmpty());
    assertEquals(SCHEDULED_OUTBOUND, env.tripData(TRIP_1_ID).showTimetable());
  }

  private TransitTestEnvironment envWithBothDirections() {
    return envBuilder
      .addTrip(outboundTrip, b -> b.withDirection(Direction.OUTBOUND))
      .addTrip(inboundTrip, b -> b.withDirection(Direction.INBOUND))
      .build();
  }

  private TripUpdate delayedUpdate(GtfsRtTestHelper rt, int directionId) {
    return fuzzyUpdate(rt)
      .withDirectionId(directionId)
      .addDelayedStopTime(LAST_STOP_SEQUENCE, DELAY)
      .build();
  }

  /**
   * An update carrying a trip id no schedule has, so that both implementations fall back on fuzzy
   * matching.
   */
  private TripUpdateBuilder fuzzyUpdate(GtfsRtTestHelper rt) {
    return rt.tripUpdateScheduled(UNKNOWN_TRIP_ID).withRouteId(ROUTE_ID).withStartTime(START_TIME);
  }
}
