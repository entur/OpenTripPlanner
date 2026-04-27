package org.opentripplanner.ext.carpooling.filter;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opentripplanner._support.time.ZoneIds.UTC;
import static org.opentripplanner.model.plan.PlanTestConstants.T11_00;
import static org.opentripplanner.model.plan.PlanTestConstants.T11_55;
import static org.opentripplanner.model.plan.TestItineraryBuilder.newItinerary;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Month;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opentripplanner.model.plan.Itinerary;
import org.opentripplanner.model.plan.Place;
import org.opentripplanner.transit.model._data.TransitTestEnvironment;

class DepartAfterFilterTest {

  static final LocalDate SERVICE_DAY = LocalDate.of(2020, Month.FEBRUARY, 2);
  static final Duration D30MINUTES = Duration.ofMinutes(30);
  static Place ORIGIN;
  static Place DESTINATION;
  static final DepartAfterFilter DEPART_AFTER_FILTER = new DepartAfterFilter();

  @BeforeAll
  static void setupEnvironment() {
    var builder = TransitTestEnvironment.of(SERVICE_DAY);

    ORIGIN = Place.forStop(builder.stop("A", customizer -> customizer.withCoordinate(5.0, 8.0)));
    DESTINATION = Place.forStop(
      builder.stop("B", customizer -> customizer.withCoordinate(6.0, 8.5))
    );

    builder.build();
  }

  @Test
  void accepts_isAnArriveByRequest_acceptedReturnsTrue() {
    var request = new CarpoolingRequestBuilder().withArriveBy(true).build();

    assertTrue(DEPART_AFTER_FILTER.accepts(driveAt1100(), request, null));
  }

  @Test
  void accepts_isDepartAfterRequestWithoutRequestedTime_acceptedReturnsTrue() {
    var request = new CarpoolingRequestBuilder().withArriveBy(false).build();

    assertTrue(DEPART_AFTER_FILTER.accepts(driveAt1100(), request, null));
  }

  @Test
  void accepts_departsExactlyAtRequestedTime_returnsTrue() {
    // Itinerary departs at 11:00, passenger requests depart-after 11:00 — exactly on time.
    var request = new CarpoolingRequestBuilder()
      .withArriveBy(false)
      .withRequestedDateTime(ZonedDateTime.of(SERVICE_DAY, LocalTime.of(11, 0, 0), UTC).toInstant())
      .build();

    assertTrue(DEPART_AFTER_FILTER.accepts(driveAt1100(), request, D30MINUTES));
  }

  @Test
  void accepts_departsAfterRequestedTime_returnsTrue() {
    // Itinerary departs at 11:00, passenger requested 10:30 — itinerary departs 30 min later.
    var request = new CarpoolingRequestBuilder()
      .withArriveBy(false)
      .withRequestedDateTime(
        ZonedDateTime.of(SERVICE_DAY, LocalTime.of(10, 30, 0), UTC).toInstant()
      )
      .build();

    assertTrue(DEPART_AFTER_FILTER.accepts(driveAt1100(), request, D30MINUTES));
  }

  @Test
  void accepts_departsOneMinuteBeforeRequestedTime_rejectedReturnsFalse() {
    // Itinerary departs at 11:00, passenger requested 11:01 — itinerary departed 1 minute too early.
    var request = new CarpoolingRequestBuilder()
      .withArriveBy(false)
      .withRequestedDateTime(ZonedDateTime.of(SERVICE_DAY, LocalTime.of(11, 1, 0), UTC).toInstant())
      .build();

    assertFalse(DEPART_AFTER_FILTER.accepts(driveAt1100(), request, D30MINUTES));
  }

  @Test
  void accepts_departsTooLongBeforeRequestedTime_rejectedReturnsFalse() {
    // Itinerary departs at 11:00, passenger requested 11:25 — itinerary departed 25 min too early.
    var request = new CarpoolingRequestBuilder()
      .withArriveBy(false)
      .withRequestedDateTime(
        ZonedDateTime.of(SERVICE_DAY, LocalTime.of(11, 25, 0), UTC).toInstant()
      )
      .build();

    assertFalse(DEPART_AFTER_FILTER.accepts(driveAt1100(), request, D30MINUTES));
  }

  /** Itinerary that departs at 11:00 and arrives at 11:55. */
  Itinerary driveAt1100() {
    return newItinerary(ORIGIN).drive(T11_00, T11_55, DESTINATION).build();
  }
}
