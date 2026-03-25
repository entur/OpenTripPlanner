package org.opentripplanner.ext.carpooling.filter;

import static java.time.ZoneOffset.UTC;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

public class ArriveByFilterTest {

  static final LocalDate SERVICE_DAY = LocalDate.of(2020, Month.FEBRUARY, 2);
  static final Duration D30MINUTES = Duration.ofMinutes(30);
  static TransitTestEnvironment TEST_ENVIRONMENT;
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

    TEST_ENVIRONMENT = builder.build();
  }

  @Test
  void accepts_isNotAnArriveByRequest_acceptedReturnsTrue() {
    var request = new CarpoolingRequestBuilder().withIsArriveByRequest(false).build();

    assertTrue(ARRIVE_BY_FILTER.accepts(driveArrivesAt1155(), request, null));
  }

  @Test
  void accepts_isArriveByRequestWithoutRequestedTime_acceptedReturnsTrue() {
    var request = new CarpoolingRequestBuilder().withIsArriveByRequest(true).build();

    assertTrue(ARRIVE_BY_FILTER.accepts(driveArrivesAt1155(), request, null));
  }

  @Test
  void accepts_arrivesInTimeAfterRequestedTime_acceptedReturnsTrue() {
    var request = new CarpoolingRequestBuilder()
      .withIsArriveByRequest(true)
      .withRequestedDateTime(
        ZonedDateTime.of(SERVICE_DAY, LocalTime.of(11, 25, 1), UTC).toInstant()
      )
      .build();

    assertTrue(ARRIVE_BY_FILTER.accepts(driveArrivesAt1155(), request, D30MINUTES));
  }

  @Test
  void accepts_arrivesTooLateAfterRequestedTime_rejectedReturnsFalse() {
    var request = new CarpoolingRequestBuilder()
      .withIsArriveByRequest(true)
      .withRequestedDateTime(
        ZonedDateTime.of(SERVICE_DAY, LocalTime.of(11, 24, 0), UTC).toInstant()
      )
      .build();

    assertFalse(ARRIVE_BY_FILTER.accepts(driveArrivesAt1155(), request, D30MINUTES));
  }

  @Test
  void accepts_arrivesAtLatestPossibleTime_acceptedReturnsTrue() {
    var request = new CarpoolingRequestBuilder()
      .withIsArriveByRequest(true)
      .withRequestedDateTime(
        ZonedDateTime.of(SERVICE_DAY, LocalTime.of(11, 25, 0), UTC).toInstant()
      )
      .build();

    assertTrue(ARRIVE_BY_FILTER.accepts(driveArrivesAt1155(), request, D30MINUTES));
  }

  @Test
  void accepts_arrivesAtEarlistPossibleTime_acceptedReturnsTrue() {
    var request = new CarpoolingRequestBuilder()
      .withIsArriveByRequest(true)
      .withRequestedDateTime(
        ZonedDateTime.of(SERVICE_DAY, LocalTime.of(12, 25, 0), UTC).toInstant()
      )
      .build();

    assertTrue(ARRIVE_BY_FILTER.accepts(driveArrivesAt1155(), request, D30MINUTES));
  }

  @Test
  void accepts_arrivesInTimeBeforeRequestedTime_acceptedReturnsTrue() {
    var request = new CarpoolingRequestBuilder()
      .withIsArriveByRequest(true)
      .withRequestedDateTime(
        ZonedDateTime.of(SERVICE_DAY, LocalTime.of(12, 24, 59), UTC).toInstant()
      )
      .build();

    assertTrue(ARRIVE_BY_FILTER.accepts(driveArrivesAt1155(), request, D30MINUTES));
  }

  @Test
  void accepts_arrivesTooSoonBeforeRequestedTime_rejectedReturnsFalse() {
    var request = new CarpoolingRequestBuilder()
      .withIsArriveByRequest(true)
      .withRequestedDateTime(
        ZonedDateTime.of(SERVICE_DAY, LocalTime.of(12, 25, 1), UTC).toInstant()
      )
      .build();

    assertFalse(ARRIVE_BY_FILTER.accepts(driveArrivesAt1155(), request, D30MINUTES));
  }

  @Test
  void accepts_arrivesAtRequestedTime_acceptedReturnTrue() {
    var request = new CarpoolingRequestBuilder()
      .withIsArriveByRequest(true)
      .withRequestedDateTime(
        ZonedDateTime.of(SERVICE_DAY, LocalTime.of(11, 55, 0), UTC).toInstant()
      )
      .build();

    assertTrue(ARRIVE_BY_FILTER.accepts(driveArrivesAt1155(), request, D30MINUTES));
  }

  Itinerary driveArrivesAt1155() {
    return newItinerary(ORIGIN).drive(T11_00, T11_55, DESTINATION).build();
  }
}
