package org.opentripplanner.ext.carpooling.filter;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opentripplanner._support.time.ZoneIds.UTC;
import static org.opentripplanner.model.plan.PlanTestConstants.T11_00;
import static org.opentripplanner.model.plan.PlanTestConstants.T11_55;
import static org.opentripplanner.model.plan.TestItineraryBuilder.newItinerary;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Month;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opentripplanner.model.plan.Itinerary;
import org.opentripplanner.model.plan.Place;
import org.opentripplanner.transit.model._data.TransitTestEnvironment;

class PostFiltersTest {

  static final LocalDate SERVICE_DAY = LocalDate.of(2020, Month.FEBRUARY, 2);
  static Place ORIGIN;
  static Place DESTINATION;

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
  void accepts_arriveByRequest_acceptedReturnsTrue() {
    // Trip arrives at 11:55, passenger requests arrive-by 12:00 — 5 minutes early.
    var request = new CarpoolingRequestBuilder()
      .withArriveBy(true)
      .withRequestedDateTime(ZonedDateTime.of(SERVICE_DAY, LocalTime.of(12, 0, 0), UTC).toInstant())
      .build();

    var postFilters = PostFilters.defaults();

    assertTrue(postFilters.accepts(driveArrivesAt1155(), request, null));
  }

  @Test
  void accepts_arriveByRequestTripArrivesTooLate_rejectedReturnsFalse() {
    // Trip arrives at 11:55, passenger requests arrive-by 11:00 — trip is too late.
    var request = new CarpoolingRequestBuilder()
      .withArriveBy(true)
      .withRequestedDateTime(ZonedDateTime.of(SERVICE_DAY, LocalTime.of(11, 0, 0), UTC).toInstant())
      .build();

    var postFilters = PostFilters.defaults();

    assertFalse(postFilters.accepts(driveArrivesAt1155(), request, null));
  }

  @Test
  void accepts_isNotAnArriveByRequest_acceptedReturnsTrue() {
    var request = new CarpoolingRequestBuilder().withArriveBy(false).build();

    var postFilters = PostFilters.defaults();

    assertTrue(postFilters.accepts(driveArrivesAt1155(), request, null));
  }

  @Test
  void accepts_departAfterRequest_tripDepartsOnTime_acceptedReturnsTrue() {
    // Itinerary departs at 11:00, passenger requests depart-after 11:00 — exactly on time.
    var request = new CarpoolingRequestBuilder()
      .withArriveBy(false)
      .withRequestedDateTime(ZonedDateTime.of(SERVICE_DAY, LocalTime.of(11, 0, 0), UTC).toInstant())
      .build();

    var postFilters = PostFilters.defaults();

    assertTrue(postFilters.accepts(driveArrivesAt1155(), request, null));
  }

  @Test
  void accepts_departAfterRequest_tripDepartsTooEarly_rejectedReturnsFalse() {
    // Itinerary departs at 11:00, passenger requests depart-after 11:30 — departed 30 min too early.
    var request = new CarpoolingRequestBuilder()
      .withArriveBy(false)
      .withRequestedDateTime(
        ZonedDateTime.of(SERVICE_DAY, LocalTime.of(11, 30, 0), UTC).toInstant()
      )
      .build();

    var postFilters = PostFilters.defaults();

    assertFalse(postFilters.accepts(driveArrivesAt1155(), request, null));
  }

  Itinerary driveArrivesAt1155() {
    return newItinerary(ORIGIN).drive(T11_00, T11_55, DESTINATION).build();
  }
}
