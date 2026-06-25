package org.opentripplanner.ext.carpooling.routing;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opentripplanner.ext.carpooling.CarpoolTestCoordinates.OSLO_CENTER;
import static org.opentripplanner.ext.carpooling.CarpoolTestCoordinates.OSLO_EAST;
import static org.opentripplanner.ext.carpooling.CarpoolTestCoordinates.OSLO_NORTH;
import static org.opentripplanner.ext.carpooling.CarpoolTestCoordinates.OSLO_SOUTH;
import static org.opentripplanner.ext.carpooling.CarpoolTestCoordinates.OSLO_WEST;
import static org.opentripplanner.ext.carpooling.CarpoolTripTestData.beelineLegDurations;
import static org.opentripplanner.ext.carpooling.CarpoolTripTestData.createDestinationStop;
import static org.opentripplanner.ext.carpooling.CarpoolTripTestData.createOriginStop;
import static org.opentripplanner.ext.carpooling.CarpoolTripTestData.createSimpleTrip;
import static org.opentripplanner.ext.carpooling.CarpoolTripTestData.createStopAt;
import static org.opentripplanner.ext.carpooling.CarpoolTripTestData.createTripWithCapacity;
import static org.opentripplanner.ext.carpooling.CarpoolTripTestData.createTripWithDeviationBudget;
import static org.opentripplanner.ext.carpooling.CarpoolTripTestData.createTripWithStops;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link InsertionPositionFinder}.
 * Focuses on heuristic validation: capacity and beeline delays.
 */
class InsertionPositionFinderTest {

  private InsertionPositionFinder finder;

  @BeforeEach
  void setup() {
    finder = new InsertionPositionFinder();
  }

  @Test
  void findViablePositions_simpleTrip_findsPositions() {
    var trip = createSimpleTrip(OSLO_CENTER, OSLO_NORTH);

    // Passenger picked up east of route, dropped off at destination — small compatible detour
    var viablePositions = finder.findViablePositions(
      trip,
      OSLO_EAST,
      OSLO_NORTH,
      Duration.ZERO,
      beelineLegDurations(trip)
    );

    assertFalse(viablePositions.isEmpty());
  }

  @Test
  void findViablePositions_noCapacity_rejectsPosition() {
    // Create a trip with 0 available seats
    var stops = List.of(createOriginStop(OSLO_CENTER), createDestinationStop(OSLO_NORTH));
    var trip = createTripWithCapacity(0, stops);

    var viablePositions = finder.findViablePositions(
      trip,
      OSLO_EAST,
      OSLO_WEST,
      Duration.ZERO,
      beelineLegDurations(trip)
    );

    // Should reject all positions due to capacity
    assertTrue(viablePositions.isEmpty());
  }

  @Test
  void findViablePositions_exceedsBeelineDelay_rejectsPosition() {
    // Create stops with very restrictive deviation budgets (1 second)
    var restrictiveBudget = Duration.ofSeconds(1);
    var trip = createTripWithStops(
      OSLO_CENTER,
      List.of(createStopAt(OSLO_EAST, restrictiveBudget)),
      OSLO_NORTH,
      restrictiveBudget
    );

    // Passenger going opposite direction (WEST→SOUTH) with 1s budget — all positions should be rejected
    var viablePositions = finder.findViablePositions(
      trip,
      OSLO_WEST,
      OSLO_SOUTH,
      Duration.ZERO,
      beelineLegDurations(trip)
    );

    assertTrue(viablePositions.isEmpty());
  }

  @Test
  void findViablePositions_multipleStops_checksAllCombinations() {
    var stop1 = createStopAt(OSLO_EAST);
    var stop2 = createStopAt(OSLO_WEST);
    var trip = createTripWithStops(OSLO_CENTER, List.of(stop1, stop2), OSLO_NORTH);

    var viablePositions = finder.findViablePositions(
      trip,
      OSLO_SOUTH,
      OSLO_NORTH,
      Duration.ZERO,
      beelineLegDurations(trip)
    );

    // Should evaluate multiple pickup/dropoff combinations
    // Exact count depends on beeline filtering
    assertNotNull(viablePositions);
  }

  /**
   * A detour that a beeline-vs-beeline check would reject is accepted once the baseline leg it
   * replaces is measured at its real (longer) routed duration. The delay is a difference, so the
   * baseline must be the actual routed time — not a beeline lower bound — for the heuristic to stay
   * an optimistic gate. This is the regression guard for that fix: a winding driver leg leaves the
   * passenger's near-on-route detour adding little real delay, even though the straight-line
   * baseline makes the same detour look like a large one.
   */
  @Test
  void findViablePositions_windingBaselineLeg_keepsNearOnRouteInsertion() {
    // Tight budget the straight-line detour estimate would exceed.
    var tightBudget = Duration.ofSeconds(30);
    var trip = createTripWithDeviationBudget(tightBudget, OSLO_CENTER, OSLO_NORTH);

    // Passenger pickup/dropoff sit off the straight CENTER→NORTH line, so the beeline detour is
    // a non-trivial positive number.
    var pickup = OSLO_EAST;
    var dropoff = OSLO_WEST;

    // Beeline baseline: the lone CENTER→NORTH leg is estimated at its straight-line time, so the
    // detour's extra beeline distance is charged in full and the position is rejected.
    var rejectedWithBeelineBaseline = finder.findViablePositions(
      trip,
      pickup,
      dropoff,
      Duration.ZERO,
      beelineLegDurations(trip)
    );
    assertTrue(
      rejectedWithBeelineBaseline.isEmpty(),
      "straight-line baseline should make the detour look too large for the tight budget"
    );

    // Actual baseline: the same leg actually takes much longer to drive (a winding road), so the
    // detour adds little beyond it and the position must survive for routing to evaluate.
    var windingBaseline = new Duration[] { Duration.ofHours(1) };
    var keptWithActualBaseline = finder.findViablePositions(
      trip,
      pickup,
      dropoff,
      Duration.ZERO,
      windingBaseline
    );
    assertFalse(
      keptWithActualBaseline.isEmpty(),
      "measuring the detour against the real (longer) baseline must keep the feasible insertion"
    );
  }
}
