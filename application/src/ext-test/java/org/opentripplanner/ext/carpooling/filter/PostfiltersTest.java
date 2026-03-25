package org.opentripplanner.ext.carpooling.filter;

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

public class PostfiltersTest {

  static final LocalDate SERVICE_DAY = LocalDate.of(2020, Month.FEBRUARY, 2);
  static final Duration D30MINUTES = Duration.ofMinutes(30);
  static TransitTestEnvironment TEST_ENVIRONMENT;
  static Place ORIGIN;
  static Place DESTINATION;

  @BeforeAll
  static void setupEnvironment() {
    var builder = TransitTestEnvironment.of(SERVICE_DAY);

    ORIGIN = Place.forStop(builder.stop("A", customizer -> customizer.withCoordinate(5.0, 8.0)));
    DESTINATION = Place.forStop(
      builder.stop("B", customizer -> customizer.withCoordinate(6.0, 8.5))
    );

    TEST_ENVIRONMENT = builder.build();
  }

  @Test
  void accepts_arriveByRequest_acceptedReturnsTrue() {
    var request = new CarpoolingRequestBuilder()
      .withIsArriveByRequest(true)
      .withRequestedDateTime(
        ZonedDateTime.of(SERVICE_DAY, LocalTime.of(11, 25, 0), UTC).toInstant()
      )
      .build();

    var postFilters = Postfilters.defaults();

    assertTrue(postFilters.accepts(driveArrivesAt1155(), request, D30MINUTES));
  }

  @Test
  void accepts_isNotAnArriveByRequest_acceptedReturnsTrue() {
    var request = new CarpoolingRequestBuilder().withIsArriveByRequest(false).build();

    var postFilters = Postfilters.defaults();

    assertTrue(postFilters.accepts(driveArrivesAt1155(), request, null));
  }

  Itinerary driveArrivesAt1155() {
    return newItinerary(ORIGIN).drive(T11_00, T11_55, DESTINATION).build();
  }
}
