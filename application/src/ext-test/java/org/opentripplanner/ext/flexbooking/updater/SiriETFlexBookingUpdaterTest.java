package org.opentripplanner.ext.flexbooking.updater;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.opentripplanner.core.model.id.FeedScopedIdForTestFactory.id;
import static org.opentripplanner.ext.flexbooking.FlexBookingEstimatedVehicleJourneyData.cancelledJourney;
import static org.opentripplanner.ext.flexbooking.FlexBookingEstimatedVehicleJourneyData.completeJourney;
import static org.opentripplanner.ext.flexbooking.FlexBookingEstimatedVehicleJourneyData.journeyWithSingleCall;
import static org.opentripplanner.ext.flexbooking.FlexBookingEstimatedVehicleJourneyData.journeyWithoutFlexibleAreas;
import static org.opentripplanner.ext.flexbooking.FlexBookingEstimatedVehicleJourneyData.journeyWithoutFramedRef;
import static org.opentripplanner.ext.flexbooking.FlexBookingTestData.FEED_ID;
import static org.opentripplanner.ext.flexbooking.FlexBookingTestData.SERVICE_DATE;
import static org.opentripplanner.ext.flexbooking.FlexBookingTestData.START;
import static org.opentripplanner.ext.flexbooking.FlexBookingTestData.UNSCHEDULED_TRIP_ID;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opentripplanner.ext.flexbooking.FlexBookingTestData;
import org.opentripplanner.ext.flexbooking.internal.DefaultFlexBookingRepository;
import org.opentripplanner.framework.io.HttpHeaders;
import org.opentripplanner.transit.model.timetable.TripIdAndServiceDate;
import org.opentripplanner.updater.trip.siri.updater.DefaultSiriETUpdaterParameters;

class SiriETFlexBookingUpdaterTest {

  private static final TripIdAndServiceDate KEY = new TripIdAndServiceDate(
    id(UNSCHEDULED_TRIP_ID),
    SERVICE_DATE
  );

  private DefaultFlexBookingRepository repository;
  private SiriETFlexBookingUpdater updater;

  @BeforeEach
  void setUp() {
    repository = new DefaultFlexBookingRepository();
    var params = new DefaultSiriETUpdaterParameters(
      "flex-booking-test",
      FEED_ID,
      false,
      "http://localhost/never-fetched",
      Duration.ofMinutes(1),
      "test-requestor",
      Duration.ofSeconds(30),
      Duration.ofMinutes(15),
      false,
      HttpHeaders.empty(),
      false
    );
    updater = new SiriETFlexBookingUpdater(
      params,
      repository,
      FlexBookingTestData.timetableRepository()
    );
  }

  @Test
  void activeJourneyUpsertsTour() {
    updater.processEstimatedVehicleJourney(
      completeJourney(UNSCHEDULED_TRIP_ID, SERVICE_DATE, START)
    );

    assertThat(repository.findTour(KEY)).isPresent();
  }

  @Test
  void cancellationRemovesTour() {
    seedActiveTour();

    updater.processEstimatedVehicleJourney(
      cancelledJourney(UNSCHEDULED_TRIP_ID, SERVICE_DATE, START)
    );

    assertThat(repository.findTour(KEY)).isEmpty();
  }

  @Test
  void journeyWithFewerThanTwoCallsRemovesTour() {
    seedActiveTour();

    updater.processEstimatedVehicleJourney(
      journeyWithSingleCall(UNSCHEDULED_TRIP_ID, SERVICE_DATE, START)
    );

    assertThat(repository.findTour(KEY)).isEmpty();
  }

  @Test
  void journeyForUnknownTripIsANoOp() {
    updater.processEstimatedVehicleJourney(completeJourney("unknown", SERVICE_DATE, START));

    assertThat(repository.listTours()).isEmpty();
  }

  @Test
  void journeyWithoutFramedRefIsANoOp() {
    updater.processEstimatedVehicleJourney(
      journeyWithoutFramedRef(UNSCHEDULED_TRIP_ID, SERVICE_DATE, START)
    );

    assertThat(repository.listTours()).isEmpty();
  }

  @Test
  void malformedNonCancellationKeepsExistingTour() {
    var malformed = journeyWithoutFlexibleAreas(UNSCHEDULED_TRIP_ID, SERVICE_DATE, START);
    // Sanity-check the fixture: a direct mapper call must throw, otherwise this test would
    // silently degrade into "upsert replaces the seeded tour" and still pass.
    assertThrows(Exception.class, () ->
      new FlexBookingSiriMapper(FEED_ID, FlexBookingTestData.timetableRepository()).mapToTour(
        malformed
      )
    );

    seedActiveTour();
    var seeded = repository.findTour(KEY).orElseThrow();

    updater.processEstimatedVehicleJourney(malformed);

    assertThat(repository.findTour(KEY)).hasValue(seeded);
  }

  private void seedActiveTour() {
    updater.processEstimatedVehicleJourney(
      completeJourney(UNSCHEDULED_TRIP_ID, SERVICE_DATE, START)
    );
    assertThat(repository.findTour(KEY)).isPresent();
  }
}
