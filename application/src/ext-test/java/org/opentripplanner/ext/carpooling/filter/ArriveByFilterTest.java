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

class ArriveByFilterTest {

  static final LocalDate SERVICE_DAY = LocalDate.of(2020, Month.FEBRUARY, 2);
  static Place ORIGIN;
  static Place DESTINATION;
  static final ArriveByFilter ARRIVE_BY_FILTER = new ArriveByFilter();

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
  void accepts_isNotAnArriveByRequest_acceptedReturnsTrue() {
    var request = new CarpoolingRequestBuilder().withArriveBy(false).build();

    assertTrue(ARRIVE_BY_FILTER.accepts(driveArrivesAt1155(), request, null));
  }

  @Test
  void accepts_isArriveByRequestWithoutRequestedTime_acceptedReturnsTrue() {
    var request = new CarpoolingRequestBuilder().withArriveBy(true).build();

    assertTrue(ARRIVE_BY_FILTER.accepts(driveArrivesAt1155(), request, null));
  }

  @Test
  void accepts_tripArrivesOneSecondAfterDeadline_rejectedReturnsFalse() {
    // Trip arrives at 11:55, deadline is 11:54:59 — one second late.
    var request = new CarpoolingRequestBuilder()
      .withArriveBy(true)
      .withRequestedDateTime(
        ZonedDateTime.of(SERVICE_DAY, LocalTime.of(11, 54, 59), UTC).toInstant()
      )
      .build();

    assertFalse(ARRIVE_BY_FILTER.accepts(driveArrivesAt1155(), request, null));
  }

  @Test
  void accepts_tripArrivesAfterDeadline_rejectedReturnsFalse() {
    var request = new CarpoolingRequestBuilder()
      .withArriveBy(true)
      .withRequestedDateTime(
        ZonedDateTime.of(SERVICE_DAY, LocalTime.of(11, 24, 0), UTC).toInstant()
      )
      .build();

    assertFalse(ARRIVE_BY_FILTER.accepts(driveArrivesAt1155(), request, null));
  }

  @Test
  void accepts_tripArrivesBeforeDeadline_acceptedReturnsTrue() {
    var request = new CarpoolingRequestBuilder()
      .withArriveBy(true)
      .withRequestedDateTime(ZonedDateTime.of(SERVICE_DAY, LocalTime.of(12, 0, 0), UTC).toInstant())
      .build();

    assertTrue(ARRIVE_BY_FILTER.accepts(driveArrivesAt1155(), request, null));
  }

  @Test
  void accepts_tripArrivesWellBeforeDeadline_acceptedReturnsTrue() {
    // Trip arrives at 11:55, deadline is 15:00 — several hours early, still accepted (no lower bound).
    var request = new CarpoolingRequestBuilder()
      .withArriveBy(true)
      .withRequestedDateTime(ZonedDateTime.of(SERVICE_DAY, LocalTime.of(15, 0, 0), UTC).toInstant())
      .build();

    assertTrue(ARRIVE_BY_FILTER.accepts(driveArrivesAt1155(), request, null));
  }

  @Test
  void accepts_tripArrivesAtExactDeadline_acceptedReturnsTrue() {
    var request = new CarpoolingRequestBuilder()
      .withArriveBy(true)
      .withRequestedDateTime(
        ZonedDateTime.of(SERVICE_DAY, LocalTime.of(11, 55, 0), UTC).toInstant()
      )
      .build();

    assertTrue(ARRIVE_BY_FILTER.accepts(driveArrivesAt1155(), request, null));
  }

  @Test
  void accepts_searchWindowIsIgnored_earlyArrivalIsAccepted() {
    // Trip arrives at 11:55, deadline 15:00, searchWindow 30 min — the 3-hour lead must not
    // cause rejection; the window is not applied to arrive-by post-filtering.
    var request = new CarpoolingRequestBuilder()
      .withArriveBy(true)
      .withRequestedDateTime(ZonedDateTime.of(SERVICE_DAY, LocalTime.of(15, 0, 0), UTC).toInstant())
      .build();

    assertTrue(ARRIVE_BY_FILTER.accepts(driveArrivesAt1155(), request, Duration.ofMinutes(30)));
  }

  Itinerary driveArrivesAt1155() {
    return newItinerary(ORIGIN).drive(T11_00, T11_55, DESTINATION).build();
  }
}
