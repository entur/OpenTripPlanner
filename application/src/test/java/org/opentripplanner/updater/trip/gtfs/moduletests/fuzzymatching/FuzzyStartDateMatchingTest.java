package org.opentripplanner.updater.trip.gtfs.moduletests.fuzzymatching;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opentripplanner.updater.spi.UpdateErrorType.TRIP_NOT_FOUND;
import static org.opentripplanner.updater.spi.UpdateResultAssertions.assertFailure;
import static org.opentripplanner.updater.spi.UpdateResultAssertions.assertSuccess;

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
 * A trip is identified for fuzzy matching by the service date the feed reported, and by nothing else.
 * An update that leaves {@code start_date} out is still applied - on the current date, guessed on the
 * feed's behalf - but it cannot be matched, because a guessed date would pick out whichever trip runs
 * today rather than the one the update is about.
 * <p>
 * The service date here is also the date the implementations guess, so that the missing field is the
 * only thing that can stand in the way of a match.
 */
class FuzzyStartDateMatchingTest implements RealtimeTestConstants {

  private static final String ROUTE_ID = "route-1";
  private static final String UNKNOWN_TRIP_ID = "not-in-the-schedule";
  private static final LocalTime START_TIME = LocalTime.of(10, 0);
  private static final int OUTBOUND_DIRECTION_ID = 0;
  private static final int LAST_STOP_SEQUENCE = 1;
  private static final int DELAY = 60;

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
  void matchesOnTheReportedDate() {
    var env = env();
    var rt = GtfsRtTestHelper.ofFuzzyMatching(env);

    var result = rt.applyTripUpdate(fuzzyUpdate(rt).build());

    assertSuccess(result);
    assertEquals("U | A [ND] 10:00 10:00 | B 10:11 10:11", env.tripData(TRIP_1_ID).showTimetable());
  }

  /**
   * The date the update is applied on would have matched, but the feed never reported it.
   */
  @Test
  void doesNotMatchWithoutAStartDate() {
    var env = env();
    var rt = GtfsRtTestHelper.ofFuzzyMatching(env);

    var result = rt.applyTripUpdate(fuzzyUpdate(rt).withoutStartDate().build());

    // The failed match has no verdict of its own: the update is rejected for what it said, a trip
    // id no schedule has.
    assertFailure(TRIP_NOT_FOUND, result);
    assertTrue(env.timetableSnapshot().isEmpty());
    assertEquals(SCHEDULED, env.tripData(TRIP_1_ID).showTimetable());
  }

  @Test
  void doesNotMatchADateTheTripDoesNotRunOn() {
    var env = env();
    var rt = GtfsRtTestHelper.ofFuzzyMatching(env);

    var result = rt.applyTripUpdate(
      fuzzyUpdate(rt).withStartDate(env.defaultServiceDate().plusDays(1)).build()
    );

    assertFailure(TRIP_NOT_FOUND, result);
    assertTrue(env.timetableSnapshot().isEmpty());
    assertEquals(SCHEDULED, env.tripData(TRIP_1_ID).showTimetable());
  }

  private TransitTestEnvironment env() {
    return envBuilder.addTrip(trip, b -> b.withDirection(Direction.OUTBOUND)).build();
  }

  /**
   * An update carrying a trip id no schedule has, so that both implementations fall back on fuzzy
   * matching, and everything the match needs: route, direction, start time and - by default - the
   * start date.
   */
  private TripUpdateBuilder fuzzyUpdate(GtfsRtTestHelper rt) {
    return rt
      .tripUpdateScheduled(UNKNOWN_TRIP_ID)
      .withRouteId(ROUTE_ID)
      .withStartTime(START_TIME)
      .withDirectionId(OUTBOUND_DIRECTION_ID)
      .addDelayedStopTime(LAST_STOP_SEQUENCE, DELAY);
  }
}
