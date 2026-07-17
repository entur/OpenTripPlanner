package org.opentripplanner.ext.flexbooking.updater;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.opentripplanner.core.model.id.FeedScopedIdForTestFactory.id;
import static org.opentripplanner.ext.flexbooking.FlexBookingEstimatedVehicleJourneyData.completeJourney;
import static org.opentripplanner.ext.flexbooking.FlexBookingEstimatedVehicleJourneyData.journeyWithBadDataFrame;
import static org.opentripplanner.ext.flexbooking.FlexBookingEstimatedVehicleJourneyData.journeyWithSingleCall;
import static org.opentripplanner.ext.flexbooking.FlexBookingEstimatedVehicleJourneyData.journeyWithoutFlexibleAreas;
import static org.opentripplanner.ext.flexbooking.FlexBookingEstimatedVehicleJourneyData.journeyWithoutFramedRef;
import static org.opentripplanner.ext.flexbooking.FlexBookingTestData.FEED_ID;
import static org.opentripplanner.ext.flexbooking.FlexBookingTestData.SCHEDULED_DEVIATED_TRIP_ID;
import static org.opentripplanner.ext.flexbooking.FlexBookingTestData.SERVICE_DATE;
import static org.opentripplanner.ext.flexbooking.FlexBookingTestData.START;
import static org.opentripplanner.ext.flexbooking.FlexBookingTestData.UNSCHEDULED_TRIP_ID;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.opentripplanner.ext.flexbooking.FlexBookingTestData;
import org.opentripplanner.transit.model.timetable.TripIdAndServiceDate;

class FlexBookingSiriMapperTest {

  private final FlexBookingSiriMapper mapper = new FlexBookingSiriMapper(
    FEED_ID,
    FlexBookingTestData.timetableRepository()
  );

  @Test
  void resolvesTourKeyFromFramedVehicleJourneyRef() {
    var key = mapper.resolveTourKey(completeJourney(UNSCHEDULED_TRIP_ID, SERVICE_DATE, START));

    assertThat(key).hasValue(new TripIdAndServiceDate(id(UNSCHEDULED_TRIP_ID), SERVICE_DATE));
  }

  @Test
  void resolveTourKeyWithoutFramedRefIsEmpty() {
    var journey = journeyWithoutFramedRef(UNSCHEDULED_TRIP_ID, SERVICE_DATE, START);

    assertThat(mapper.resolveTourKey(journey)).isEmpty();
  }

  @Test
  void resolveTourKeyWithUnparseableDataFrameIsEmpty() {
    var journey = journeyWithBadDataFrame(UNSCHEDULED_TRIP_ID, SERVICE_DATE, START);

    assertThat(mapper.resolveTourKey(journey)).isEmpty();
  }

  @Test
  void acceptsActiveUnscheduledTrip() {
    assertThat(
      mapper.isActiveUnscheduledTrip(
        new TripIdAndServiceDate(id(UNSCHEDULED_TRIP_ID), SERVICE_DATE)
      )
    ).isTrue();
  }

  @Test
  void rejectsUnknownTrip() {
    assertThat(
      mapper.isActiveUnscheduledTrip(new TripIdAndServiceDate(id("unknown"), SERVICE_DATE))
    ).isFalse();
  }

  @Test
  void rejectsScheduledDeviatedTrip() {
    assertThat(
      mapper.isActiveUnscheduledTrip(
        new TripIdAndServiceDate(id(SCHEDULED_DEVIATED_TRIP_ID), SERVICE_DATE)
      )
    ).isFalse();
  }

  @Test
  void rejectsInactiveServiceDate() {
    assertThat(
      mapper.isActiveUnscheduledTrip(
        new TripIdAndServiceDate(id(UNSCHEDULED_TRIP_ID), SERVICE_DATE.plusDays(1))
      )
    ).isFalse();
  }

  @Test
  void mapsJourneyToTour() {
    var tour = mapper.mapToTour(completeJourney(UNSCHEDULED_TRIP_ID, SERVICE_DATE, START));

    assertThat(tour).isNotNull();
    assertThat(tour.stops()).hasSize(3);
    // The vehicle anchor is never displaced; the booked stops carry their remaining budgets.
    assertThat(tour.stops().get(0).getDeviationBudget()).isEqualTo(Duration.ZERO);
    assertThat(tour.stops().get(1).getDeviationBudget()).isEqualTo(Duration.ofMinutes(15));
    assertThat(tour.stops().get(2).getDeviationBudget()).isEqualTo(Duration.ofMinutes(10));
    assertThat(tour.stops().get(1).getOnboardCount()).isEqualTo(2);
    assertThat(tour.startTime()).isEqualTo(START);
    assertThat(tour.endTime()).isEqualTo(START.plusMinutes(45));
  }

  @Test
  void mapsJourneySpanningMoreThanTheCarpoolMaximum() {
    // 5 hours end to end — over the carpool mapper's 2.5 h default, fine for a flex tour.
    var journey = completeJourney(UNSCHEDULED_TRIP_ID, SERVICE_DATE, START);
    var dropoff = journey.getEstimatedCalls().getEstimatedCalls().getLast();
    dropoff.setAimedArrivalTime(START.plusHours(5));
    dropoff.setExpectedArrivalTime(START.plusHours(5));
    dropoff.setLatestExpectedArrivalTime(START.plusHours(5).plusMinutes(10));

    assertThat(mapper.mapToTour(journey)).isNotNull();
  }

  @Test
  void mapsSingleCallJourneyToNull() {
    var journey = journeyWithSingleCall(UNSCHEDULED_TRIP_ID, SERVICE_DATE, START);

    assertThat(mapper.mapToTour(journey)).isNull();
  }

  @Test
  void rejectsJourneyWithoutEstimatedVehicleJourneyCode() {
    var journey = completeJourney(UNSCHEDULED_TRIP_ID, SERVICE_DATE, START);
    journey.setEstimatedVehicleJourneyCode(null);

    assertThrows(IllegalArgumentException.class, () -> mapper.mapToTour(journey));
  }

  @Test
  void rejectsJourneyWithoutOperatorRef() {
    var journey = completeJourney(UNSCHEDULED_TRIP_ID, SERVICE_DATE, START);
    journey.setOperatorRef(null);

    assertThrows(IllegalArgumentException.class, () -> mapper.mapToTour(journey));
  }

  @Test
  void rejectsJourneyWithoutFlexibleAreas() {
    var journey = journeyWithoutFlexibleAreas(UNSCHEDULED_TRIP_ID, SERVICE_DATE, START);

    assertThrows(IllegalArgumentException.class, () -> mapper.mapToTour(journey));
  }
}
