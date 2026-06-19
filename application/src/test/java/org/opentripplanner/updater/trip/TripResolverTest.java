package org.opentripplanner.updater.trip;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.core.model.id.FeedScopedIdForTestFactory;
import org.opentripplanner.transit.model._data.TransitTestEnvironment;
import org.opentripplanner.transit.model._data.TripInput;
import org.opentripplanner.transit.service.TransitService;
import org.opentripplanner.updater.spi.UpdateErrorType;
import org.opentripplanner.updater.spi.UpdateException;
import org.opentripplanner.updater.trip.model.TripReference;

/**
 * Tests for {@link TripResolver}.
 */
class TripResolverTest {

  private static final String FEED_ID = FeedScopedIdForTestFactory.FEED_ID;
  private static final String TRIP_ID = "trip1";
  private static final String TRIP_ON_SERVICE_DATE_ID = "dated-trip1";

  private TransitService transitService;
  private TripResolver resolver;

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
          .withWithTripOnServiceDate(TRIP_ON_SERVICE_DATE_ID)
      )
      .build();

    transitService = env.transitService();
    resolver = new TripResolver(transitService);
  }

  @Test
  void resolveTripByTripId() {
    var tripId = new FeedScopedId(FEED_ID, TRIP_ID);
    var reference = TripReference.ofTripId(tripId);

    var trip = resolver.resolveTrip(reference);

    assertNotNull(trip);
    assertEquals(tripId, trip.getId());
  }

  @Test
  void resolveTripByTripOnServiceDateId() {
    var tripOnServiceDateId = new FeedScopedId(FEED_ID, TRIP_ON_SERVICE_DATE_ID);
    var reference = TripReference.builder().withTripOnServiceDateId(tripOnServiceDateId).build();

    var trip = resolver.resolveTrip(reference);

    assertNotNull(trip);
    // The resolved trip should be the underlying trip from the TripOnServiceDate
    assertEquals(new FeedScopedId(FEED_ID, TRIP_ID), trip.getId());
  }

  @Test
  void resolveTripWithBothIds_prefersDirectTripId() {
    // When both tripId and tripOnServiceDateId are provided, tripId takes precedence
    var tripId = new FeedScopedId(FEED_ID, TRIP_ID);
    var tripOnServiceDateId = new FeedScopedId(FEED_ID, TRIP_ON_SERVICE_DATE_ID);
    var reference = TripReference.builder()
      .withTripId(tripId)
      .withTripOnServiceDateId(tripOnServiceDateId)
      .build();

    var trip = resolver.resolveTrip(reference);

    assertNotNull(trip);
    assertEquals(tripId, trip.getId());
  }

  @Test
  void resolveTripWithUnknownTripId_throwsException() {
    var unknownTripId = new FeedScopedId(FEED_ID, "unknown-trip");
    var reference = TripReference.ofTripId(unknownTripId);

    var exception = assertThrows(UpdateException.class, () -> resolver.resolveTrip(reference));
    assertEquals(UpdateErrorType.TRIP_NOT_FOUND, exception.errorType());
  }

  @Test
  void resolveTripWithUnknownTripOnServiceDateId_throwsException() {
    var unknownId = new FeedScopedId(FEED_ID, "unknown-dated-trip");
    var reference = TripReference.builder().withTripOnServiceDateId(unknownId).build();

    var exception = assertThrows(UpdateException.class, () -> resolver.resolveTrip(reference));
    assertEquals(UpdateErrorType.TRIP_NOT_FOUND, exception.errorType());
  }

  @Test
  void resolveTripWithNoIds_throwsException() {
    var reference = TripReference.builder().build();

    var exception = assertThrows(UpdateException.class, () -> resolver.resolveTrip(reference));
    assertEquals(UpdateErrorType.TRIP_NOT_FOUND, exception.errorType());
  }

  @Test
  void resolveTripOrNull_returnsTrip() {
    var tripId = new FeedScopedId(FEED_ID, TRIP_ID);
    var reference = TripReference.ofTripId(tripId);

    var trip = resolver.resolveTripOrNull(reference);

    assertNotNull(trip);
    assertEquals(tripId, trip.getId());
  }

  @Test
  void resolveTripOrNull_returnsNullWhenNotFound() {
    var unknownTripId = new FeedScopedId(FEED_ID, "unknown-trip");
    var reference = TripReference.ofTripId(unknownTripId);

    var trip = resolver.resolveTripOrNull(reference);

    assertNull(trip);
  }
}
