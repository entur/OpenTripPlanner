package org.opentripplanner.ext.carpooling.filter;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opentripplanner._support.time.ZoneIds.UTC;
import static org.opentripplanner.ext.carpooling.CarpoolItineraryTestData.SERVICE_DAY;
import static org.opentripplanner.ext.carpooling.CarpoolItineraryTestData.driveItinerary;
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
import java.time.LocalTime;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;

class TimeItineraryFilterTest {

  // Itinerary departs at 11:00 UTC, arrives at 11:55 UTC (on SERVICE_DAY = 2020-02-02).
  static final Duration WINDOW = Duration.ofMinutes(30);
  static final TimeItineraryFilter FILTER = new TimeItineraryFilter();

  // ---------------------------------------------------------------------------
  // Pass-through: no requestedDateTime
  // ---------------------------------------------------------------------------

  @Test
  void departAfterWithNoTime_returnsTrue() {
    assertTrue(FILTER.isValidItinerary(driveItinerary(), departAfterWithNoTime(), WINDOW));
  }

  @Test
  void arriveByWithNoTime_returnsTrue() {
    assertTrue(FILTER.isValidItinerary(driveItinerary(), arriveByWithNoTime(), WINDOW));
  }

  // ===========================================================================
  // Depart-after  (EDT = requestedDateTime, LDT = EDT + searchWindow)
  // Itinerary startTime must lie in [EDT, LDT].
  // Identical bounds for direct / access / egress.
  // ===========================================================================

  // -- Direct -----------------------------------------------------------------

  @Test
  void departAfterDirect_within_window_returnsTrue() {
    // Itinerary departs at 11:00; EDT=10:30 → LDT=11:00 — at the upper bound.
    assertTrue(FILTER.isValidItinerary(driveItinerary(), departAfterDirect(at(10, 30)), WINDOW));
  }

  @Test
  void departAfterDirect_itineraryDepartsAtEDT_returnsTrue() {
    assertTrue(FILTER.isValidItinerary(driveItinerary(), departAfterDirect(at(11, 0)), WINDOW));
  }

  @Test
  void departAfterDirect_itineraryDepartsBeforeEDT_returnsFalse() {
    // EDT 11:01 > itinerary departure 11:00.
    assertFalse(FILTER.isValidItinerary(driveItinerary(), departAfterDirect(at(11, 1)), WINDOW));
  }

  @Test
  void departAfterDirect_itineraryDepartsAtLDT_returnsTrue() {
    // EDT=10:30 → LDT=11:00 = itinerary departure.
    assertTrue(FILTER.isValidItinerary(driveItinerary(), departAfterDirect(at(10, 30)), WINDOW));
  }

  @Test
  void departAfterDirect_itineraryDepartsPastLDT_returnsFalse() {
    // EDT=10:29 → LDT=10:59 < itinerary departure 11:00.
    assertFalse(FILTER.isValidItinerary(driveItinerary(), departAfterDirect(at(10, 29)), WINDOW));
  }

  // -- Access (same bounds) ---------------------------------------------------

  @Test
  void departAfterAccess_within_window_returnsTrue() {
    assertTrue(FILTER.isValidItinerary(driveItinerary(), departAfterAccess(at(10, 30)), WINDOW));
  }

  @Test
  void departAfterAccess_itineraryDepartsPastLDT_returnsFalse() {
    assertFalse(FILTER.isValidItinerary(driveItinerary(), departAfterAccess(at(10, 14)), WINDOW));
  }

  // -- Egress (same bounds) ---------------------------------------------------

  @Test
  void departAfterEgress_within_window_returnsTrue() {
    assertTrue(FILTER.isValidItinerary(driveItinerary(), departAfterEgress(at(10, 30)), WINDOW));
  }

  @Test
  void departAfterEgress_itineraryPastLDT_returnsFalse() {
    // EDT = 25 h before the itinerary departure → LDT = EDT + 30 min still way before.
    var edt = ZonedDateTime.of(SERVICE_DAY, LocalTime.of(11, 0), UTC).minusHours(25).toInstant();
    assertFalse(FILTER.isValidItinerary(driveItinerary(), departAfterEgress(edt), WINDOW));
  }

  // ===========================================================================
  // Arrive-by  (LAT = requestedDateTime, EAT = LAT − searchWindow)
  // Itinerary endTime must lie in [EAT, LAT].
  // Identical bounds for direct / access / egress.
  // ===========================================================================

  // -- Direct -----------------------------------------------------------------

  @Test
  void arriveByDirect_within_window_returnsTrue() {
    // Itinerary arrives at 11:55; LAT=12:00 — comfortably before.
    assertTrue(FILTER.isValidItinerary(driveItinerary(), arriveByDirect(at(12, 0)), WINDOW));
  }

  @Test
  void arriveByDirect_itineraryArrivesAtLAT_returnsTrue() {
    assertTrue(FILTER.isValidItinerary(driveItinerary(), arriveByDirect(at(11, 55)), WINDOW));
  }

  @Test
  void arriveByDirect_itineraryArrivesAfterLAT_returnsFalse() {
    // LAT 11:54 < itinerary arrival 11:55.
    assertFalse(FILTER.isValidItinerary(driveItinerary(), arriveByDirect(at(11, 54)), WINDOW));
  }

  @Test
  void arriveByDirect_itineraryArrivesAtEAT_returnsTrue() {
    // LAT=12:25 → EAT=11:55 = itinerary arrival.
    assertTrue(FILTER.isValidItinerary(driveItinerary(), arriveByDirect(at(12, 25)), WINDOW));
  }

  @Test
  void arriveByDirect_itineraryArrivesBeforeEAT_returnsFalse() {
    // LAT=12:26 → EAT=11:56 > itinerary arrival 11:55.
    assertFalse(FILTER.isValidItinerary(driveItinerary(), arriveByDirect(at(12, 26)), WINDOW));
  }

  // -- Access (same bounds) ---------------------------------------------------

  @Test
  void arriveByAccess_within_window_returnsTrue() {
    assertTrue(FILTER.isValidItinerary(driveItinerary(), arriveByAccess(at(12, 0)), WINDOW));
  }

  @Test
  void arriveByAccess_itineraryArrivesAfterLAT_returnsFalse() {
    assertFalse(FILTER.isValidItinerary(driveItinerary(), arriveByAccess(at(11, 54)), WINDOW));
  }

  // -- Egress (same bounds) ---------------------------------------------------

  @Test
  void arriveByEgress_within_window_returnsTrue() {
    assertTrue(FILTER.isValidItinerary(driveItinerary(), arriveByEgress(at(12, 0)), WINDOW));
  }

  @Test
  void arriveByEgress_itineraryArrivesAfterLAT_returnsFalse() {
    // LAT = 1 h before the itinerary arrival.
    var lat = ZonedDateTime.of(SERVICE_DAY, LocalTime.of(11, 55), UTC).minusHours(1).toInstant();
    assertFalse(FILTER.isValidItinerary(driveItinerary(), arriveByEgress(lat), WINDOW));
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private static Instant at(int hour, int minute) {
    return ZonedDateTime.of(SERVICE_DAY, LocalTime.of(hour, minute), UTC).toInstant();
  }
}
