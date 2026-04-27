package org.opentripplanner.ext.carpooling.filter;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opentripplanner.ext.carpooling.CarpoolTestCoordinates.OSLO_CENTER;
import static org.opentripplanner.ext.carpooling.CarpoolTestCoordinates.OSLO_NORTH;
import static org.opentripplanner.ext.carpooling.CarpoolTripTestData.createSimpleTripWithTimes;
import static org.opentripplanner.ext.carpooling.CarpoolingRequestTestData.arriveByAccess;
import static org.opentripplanner.ext.carpooling.CarpoolingRequestTestData.arriveByDirect;
import static org.opentripplanner.ext.carpooling.CarpoolingRequestTestData.arriveByEgress;
import static org.opentripplanner.ext.carpooling.CarpoolingRequestTestData.arriveByWithNoTime;
import static org.opentripplanner.ext.carpooling.CarpoolingRequestTestData.departAfterAccess;
import static org.opentripplanner.ext.carpooling.CarpoolingRequestTestData.departAfterDirect;
import static org.opentripplanner.ext.carpooling.CarpoolingRequestTestData.departAfterEgress;
import static org.opentripplanner.ext.carpooling.CarpoolingRequestTestData.departAfterWithNoTime;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;
import org.opentripplanner.ext.carpooling.model.CarpoolTrip;

class TimeTripFilterTest {

  private final TimeTripFilter filter = new TimeTripFilter();

  // Trip: starts 10:00+01:00, ends 11:00+01:00.
  private static final ZonedDateTime TRIP_START = ZonedDateTime.parse("2024-01-15T10:00:00+01:00");
  private static final ZonedDateTime TRIP_END = TRIP_START.plusHours(1);
  private static final Duration WINDOW = Duration.ofMinutes(30);

  // ---------------------------------------------------------------------------
  // Pass-through: no requestedDateTime
  // ---------------------------------------------------------------------------

  @Test
  void isCandidateTrip_departAfterWithNoTime_returnsTrue() {
    assertTrue(filter.isCandidateTrip(trip(), departAfterWithNoTime(), WINDOW));
  }

  @Test
  void isCandidateTrip_arriveByWithNoTime_returnsTrue() {
    assertTrue(filter.isCandidateTrip(trip(), arriveByWithNoTime(), WINDOW));
  }

  // ===========================================================================
  // Depart-after  (EDT = requestedDateTime, LDT = EDT + searchWindow)
  // Too early: tripEnd  < EDT
  // Too late:  tripStart > LDT + slack   slack = W for direct/access, T for egress
  // ===========================================================================

  // -- Direct -----------------------------------------------------------------

  @Test
  void departAfterDirect_within_window_returnsTrue() {
    // EDT 20 min before tripStart — straightforward candidate.
    assertTrue(filter.isCandidateTrip(trip(), departAfterDirect(at(-20)), WINDOW));
  }

  @Test
  void departAfterDirect_tripUnderwayAtEDT_returnsTrue() {
    // EDT is 15 min after tripStart — passenger can still board mid-route.
    assertTrue(filter.isCandidateTrip(trip(), departAfterDirect(at(15)), WINDOW));
  }

  @Test
  void departAfterDirect_tripEndedBeforeEDT_returnsFalse() {
    // EDT 90 min after tripStart — tripEnd (= +60) is before EDT.
    assertFalse(filter.isCandidateTrip(trip(), departAfterDirect(at(90)), WINDOW));
  }

  @Test
  void departAfterDirect_tripStartAtLDTPlusW_returnsTrue() {
    // LDT + W = (EDT + 30) + 15 = EDT + 45. With at(-45), tripStart - EDT = 45 → boundary, accept.
    assertTrue(filter.isCandidateTrip(trip(), departAfterDirect(at(-45)), WINDOW));
  }

  @Test
  void departAfterDirect_tripStartPastLDTPlusW_returnsFalse() {
    // tripStart - EDT = 46 min, one minute past LDT + W.
    assertFalse(filter.isCandidateTrip(trip(), departAfterDirect(at(-46)), WINDOW));
  }

  // -- Access (same bounds as direct) -----------------------------------------

  @Test
  void departAfterAccess_within_window_returnsTrue() {
    assertTrue(filter.isCandidateTrip(trip(), departAfterAccess(at(-20)), WINDOW));
  }

  @Test
  void departAfterAccess_tripStartPastLDTPlusW_returnsFalse() {
    // Access uses W on the LDT side, same as direct — no T fallback here.
    assertFalse(filter.isCandidateTrip(trip(), departAfterAccess(at(-46)), WINDOW));
  }

  // -- Egress (LDT + T fallback) ---------------------------------------------

  @Test
  void departAfterEgress_within_T_returnsTrue() {
    // tripStart - EDT = 120 min, past direct/access LDT+W bound, but well within LDT+T.
    assertTrue(filter.isCandidateTrip(trip(), departAfterEgress(at(-120)), WINDOW));
  }

  @Test
  void departAfterEgress_past_T_returnsFalse() {
    // EDT = tripStart - (24 h + 31 min) → LDT + T = tripStart - 1 min, just past.
    assertFalse(
      filter.isCandidateTrip(
        trip(),
        departAfterEgress(TRIP_START.minusHours(24).minusMinutes(31).toInstant()),
        WINDOW
      )
    );
  }

  // ===========================================================================
  // Arrive-by  (LAT = requestedDateTime, EAT = LAT − searchWindow)
  // Too late:  tripStart > LAT
  // Too early: tripEnd  < EAT − slack    slack = T for access, W for direct/egress
  // ===========================================================================

  // -- Direct (LAT side: no slack; EAT side: W) -------------------------------

  @Test
  void arriveByDirect_within_window_returnsTrue() {
    // LAT 30 min after tripEnd — easy candidate.
    assertTrue(
      filter.isCandidateTrip(trip(), arriveByDirect(TRIP_END.plusMinutes(30).toInstant()), WINDOW)
    );
  }

  @Test
  void arriveByDirect_tripStartAtLAT_returnsTrue() {
    // LAT == tripStart — boundary, accept (isAfter is strict).
    assertTrue(filter.isCandidateTrip(trip(), arriveByDirect(TRIP_START.toInstant()), WINDOW));
  }

  @Test
  void arriveByDirect_tripStartAfterLAT_returnsFalse() {
    // LAT one minute before tripStart — driver hasn't left yet.
    assertFalse(
      filter.isCandidateTrip(trip(), arriveByDirect(TRIP_START.minusMinutes(1).toInstant()), WINDOW)
    );
  }

  @Test
  void arriveByDirect_driverContinuesPastLAT_returnsTrue() {
    // tripStart 30 min before LAT — passenger can be dropped off mid-route.
    assertTrue(
      filter.isCandidateTrip(trip(), arriveByDirect(TRIP_START.plusMinutes(30).toInstant()), WINDOW)
    );
  }

  @Test
  void arriveByDirect_tripEndsBeforeEATMinusW_returnsFalse() {
    // LAT 3 h after tripEnd → EAT = LAT − 30 = +150 min after tripEnd,
    // EAT − W = +135 min after tripEnd. tripEnd is way before that, reject as too early.
    assertFalse(
      filter.isCandidateTrip(trip(), arriveByDirect(TRIP_END.plusHours(3).toInstant()), WINDOW)
    );
  }

  // -- Access (LAT side: no slack; EAT side: T) -------------------------------

  @Test
  void arriveByAccess_within_window_returnsTrue() {
    assertTrue(
      filter.isCandidateTrip(trip(), arriveByAccess(TRIP_END.plusMinutes(30).toInstant()), WINDOW)
    );
  }

  @Test
  void arriveByAccess_tripStartAfterLAT_returnsFalse() {
    // Access uses no slack on the LAT side, same as direct.
    assertFalse(
      filter.isCandidateTrip(trip(), arriveByAccess(TRIP_START.minusMinutes(1).toInstant()), WINDOW)
    );
  }

  @Test
  void arriveByAccess_tripEndsWithin_T_returnsTrue() {
    // LAT 3 h after tripEnd → EAT − T ≈ 23 h before tripEnd; tripEnd is well after, accept.
    assertTrue(
      filter.isCandidateTrip(trip(), arriveByAccess(TRIP_END.plusHours(3).toInstant()), WINDOW)
    );
  }

  @Test
  void arriveByAccess_tripEndsBeforeEATMinusT_returnsFalse() {
    // LAT 25 h after tripEnd → EAT − T = 30 min after tripEnd, reject as too early.
    assertFalse(
      filter.isCandidateTrip(trip(), arriveByAccess(TRIP_END.plusHours(25).toInstant()), WINDOW)
    );
  }

  // -- Egress (same bounds as direct: no slack on LAT side; W on EAT side) ----

  @Test
  void arriveByEgress_within_window_returnsTrue() {
    assertTrue(
      filter.isCandidateTrip(trip(), arriveByEgress(TRIP_END.plusMinutes(30).toInstant()), WINDOW)
    );
  }

  @Test
  void arriveByEgress_tripStartAfterLAT_returnsFalse() {
    // Egress uses no slack on the LAT side — same as direct/access.
    // (This is the fix for the bug previously flagged: old behaviour granted 24 h.)
    assertFalse(
      filter.isCandidateTrip(trip(), arriveByEgress(TRIP_START.minusMinutes(1).toInstant()), WINDOW)
    );
  }

  @Test
  void arriveByEgress_tripEndsBeforeEATMinusW_returnsFalse() {
    // Same as direct — egress uses W (not T) on the EAT side.
    assertFalse(
      filter.isCandidateTrip(trip(), arriveByEgress(TRIP_END.plusHours(3).toInstant()), WINDOW)
    );
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  /** Instant expressed as minutes relative to TRIP_START (positive = after tripStart). */
  private static Instant at(int minutesFromTripStart) {
    return TRIP_START.plusMinutes(minutesFromTripStart).toInstant();
  }

  private static CarpoolTrip trip() {
    return createSimpleTripWithTimes(OSLO_CENTER, OSLO_NORTH, TRIP_START, TRIP_END);
  }
}
