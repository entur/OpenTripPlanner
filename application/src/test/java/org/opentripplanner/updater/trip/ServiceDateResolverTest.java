package org.opentripplanner.updater.trip;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.transit.model._data.FeedScopedIdForTestFactory;
import org.opentripplanner.transit.model._data.TransitTestEnvironment;
import org.opentripplanner.transit.model._data.TripInput;
import org.opentripplanner.transit.service.TransitService;
import org.opentripplanner.updater.spi.UpdateError;
import org.opentripplanner.updater.trip.model.ParsedTripUpdate;
import org.opentripplanner.updater.trip.model.TripReference;
import org.opentripplanner.updater.trip.model.TripUpdateOptions;
import org.opentripplanner.updater.trip.model.TripUpdateType;

/**
 * Tests for {@link ServiceDateResolver}.
 */
class ServiceDateResolverTest {

  private static final String FEED_ID = FeedScopedIdForTestFactory.FEED_ID;
  private static final ZoneId TIME_ZONE = ZoneId.of("Europe/Oslo");
  private static final LocalDate SERVICE_DATE = LocalDate.of(2024, 1, 15);

  @Nested
  class RegularTripTests {

    private static final String TRIP_ID = "regular-trip";

    private TransitService transitService;
    private ServiceDateResolver resolver;

    @BeforeEach
    void setUp() {
      var builder = TransitTestEnvironment.of().addStops("A", "B", "C");
      var stopA = builder.stop("A");
      var stopB = builder.stop("B");
      var stopC = builder.stop("C");

      var env = builder
        .addTrip(
          TripInput.of(TRIP_ID)
            .addStop(stopA, "10:00")
            .addStop(stopB, "10:30")
            .addStop(stopC, "11:00")
        )
        .build();

      transitService = env.transitService();
      var tripResolver = new TripResolver(transitService);
      resolver = new ServiceDateResolver(tripResolver, transitService);
    }

    @Test
    void resolveServiceDate_whenExplicitServiceDate_returnsIt() {
      var tripId = new FeedScopedId(FEED_ID, TRIP_ID);
      var tripRef = TripReference.ofTripId(tripId);
      var update = ParsedTripUpdate.builder(
        TripUpdateType.UPDATE_EXISTING,
        tripRef,
        SERVICE_DATE
      ).build();

      var result = resolver.resolveServiceDate(update);

      assertTrue(result.isSuccess());
      assertEquals(SERVICE_DATE, result.successValue());
    }

    @Test
    void resolveServiceDate_whenNoServiceDateAndNoAimedDeparture_returnsError() {
      var tripOnServiceDateId = new FeedScopedId(FEED_ID, "unknown-dsj");
      var tripRef = TripReference.builder().withTripOnServiceDateId(tripOnServiceDateId).build();
      var update = ParsedTripUpdate.builder(TripUpdateType.UPDATE_EXISTING, tripRef, null).build();

      var result = resolver.resolveServiceDate(update);

      assertTrue(result.isFailure());
      assertEquals(UpdateError.UpdateErrorType.TRIP_NOT_FOUND, result.failureValue().errorType());
    }
  }

  @Nested
  class OvernightTripTests {

    private static final String OVERNIGHT_TRIP_ID = "overnight-trip";

    private TransitService transitService;
    private ServiceDateResolver resolver;

    @BeforeEach
    void setUp() {
      var builder = TransitTestEnvironment.of().addStops("A", "B", "C");
      var stopA = builder.stop("A");
      var stopB = builder.stop("B");
      var stopC = builder.stop("C");

      // Create an overnight trip that departs at 26:00 (2:00 AM on the next calendar day)
      // but is registered on the previous service date
      var env = builder
        .addTrip(
          TripInput.of(OVERNIGHT_TRIP_ID)
            .addStop(stopA, "26:00")
            .addStop(stopB, "26:30")
            .addStop(stopC, "27:00")
            .withServiceDates(SERVICE_DATE)
        )
        .build();

      transitService = env.transitService();
      var tripResolver = new TripResolver(transitService);
      resolver = new ServiceDateResolver(tripResolver, transitService);
    }

    @Test
    void resolveServiceDate_forOvernightTrip_calculatesCorrectServiceDate() {
      // The aimed departure time is 2:00 AM on January 16th (calendar date)
      // But the service date should be January 15th (the trip is registered on the 15th with 26:00 departure)
      var aimedDepartureTime = ZonedDateTime.of(2024, 1, 16, 2, 0, 0, 0, TIME_ZONE);

      var tripId = new FeedScopedId(FEED_ID, OVERNIGHT_TRIP_ID);
      var tripRef = TripReference.ofTripId(tripId);
      var update = ParsedTripUpdate.builder(TripUpdateType.UPDATE_EXISTING, tripRef, null)
        .withAimedDepartureTime(aimedDepartureTime)
        .withOptions(TripUpdateOptions.siriDefaults())
        .build();

      var result = resolver.resolveServiceDate(update);

      assertTrue(result.isSuccess());
      // The service date should be the 15th, not the 16th
      assertEquals(SERVICE_DATE, result.successValue());
    }

    @Test
    void resolveServiceDate_forRegularTrip_noOffset() {
      // For a trip starting at 10:00 (within the same calendar day as service date),
      // the aimed departure time date should equal the service date
      var builder = TransitTestEnvironment.of().addStops("X", "Y");
      var stopX = builder.stop("X");
      var stopY = builder.stop("Y");

      var env = builder
        .addTrip(
          TripInput.of("day-trip")
            .addStop(stopX, "10:00")
            .addStop(stopY, "11:00")
            .withServiceDates(SERVICE_DATE)
        )
        .build();

      var tripResolver = new TripResolver(env.transitService());
      var dayResolver = new ServiceDateResolver(tripResolver, env.transitService());

      // Aimed departure is 10:00 AM on January 15th
      var aimedDepartureTime = ZonedDateTime.of(2024, 1, 15, 10, 0, 0, 0, TIME_ZONE);

      var tripId = new FeedScopedId(FEED_ID, "day-trip");
      var tripRef = TripReference.ofTripId(tripId);
      var update = ParsedTripUpdate.builder(TripUpdateType.UPDATE_EXISTING, tripRef, null)
        .withAimedDepartureTime(aimedDepartureTime)
        .withOptions(TripUpdateOptions.siriDefaults())
        .build();

      var result = dayResolver.resolveServiceDate(update);

      assertTrue(result.isSuccess());
      assertEquals(SERVICE_DATE, result.successValue());
    }

    @Test
    void resolveServiceDate_whenTripNotResolvable_fallsBackToSimpleDateExtraction() {
      // When the Trip cannot be resolved, fall back to extracting the date from the ZonedDateTime
      // using its embedded timezone. This matches EntityResolver's behavior.
      var aimedDepartureTime = ZonedDateTime.of(2024, 1, 16, 2, 0, 0, 0, TIME_ZONE);

      var unknownTripId = new FeedScopedId(FEED_ID, "unknown-trip");
      var tripRef = TripReference.ofTripId(unknownTripId);

      var update = ParsedTripUpdate.builder(TripUpdateType.UPDATE_EXISTING, tripRef, null)
        .withAimedDepartureTime(aimedDepartureTime)
        .withOptions(TripUpdateOptions.siriDefaults())
        .build();

      var result = resolver.resolveServiceDate(update);

      // Even though Trip resolution fails, service date should still be resolved
      // from the aimed departure time using toLocalDate() (fallback path)
      assertTrue(result.isSuccess());
      // In the fallback case without Trip data, we use ZonedDateTime.toLocalDate()
      // which returns January 16th (the calendar date of 2:00 AM)
      assertEquals(LocalDate.of(2024, 1, 16), result.successValue());
    }
  }
}
