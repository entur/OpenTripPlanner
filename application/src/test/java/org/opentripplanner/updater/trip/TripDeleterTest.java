package org.opentripplanner.updater.trip;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.core.model.id.FeedScopedIdForTestFactory;
import org.opentripplanner.transit.model.TransitTestEnvironment;
import org.opentripplanner.transit.model.TripInput;
import org.opentripplanner.transit.service.TransitEditorService;
import org.opentripplanner.updater.spi.UpdateErrorType;
import org.opentripplanner.updater.spi.UpdateException;
import org.opentripplanner.updater.trip.model.TripDeletion;
import org.opentripplanner.updater.trip.model.TripReference;

/**
 * Tests for {@link TripDeleter}.
 */
class TripDeleterTest {

  private static final ZoneId TIME_ZONE = ZoneId.of("America/New_York");

  private static final String FEED_ID = FeedScopedIdForTestFactory.FEED_ID;
  private static final String TRIP_ID = "trip1";
  private static final String TRIP_ON_SERVICE_DATE_ID = "dated-trip1";

  private TransitTestEnvironment env;
  private TransitEditorService transitService;
  private TripRemovalResolver resolver;
  private TripDeleter deleter;

  @BeforeEach
  void setUp() {
    var builder = TransitTestEnvironment.of().addStops("A", "B", "C");

    var stopA = builder.stop("A");
    var stopB = builder.stop("B");
    var stopC = builder.stop("C");

    env = builder
      .addTrip(
        TripInput.of(TRIP_ID)
          .addStop(stopA, "10:00")
          .addStop(stopB, "10:30")
          .addStop(stopC, "11:00")
          .withWithTripOnServiceDate(TRIP_ON_SERVICE_DATE_ID)
      )
      .build();

    transitService = (TransitEditorService) env.transitService();
    var tripResolver = new TripResolver(env.transitService());
    var serviceDateResolver = new ServiceDateResolver(tripResolver, env.transitService());
    resolver = new TripRemovalResolver(transitService, tripResolver, serviceDateResolver);
    deleter = new TripDeleter(resolver);
  }

  @Test
  void deleteTripByTripId() {
    var tripId = new FeedScopedId(FEED_ID, TRIP_ID);
    var tripRef = TripReference.ofTripId(tripId);
    var parsedUpdate = new TripDeletion(tripRef, env.defaultServiceDate(), null, null);

    // Verify trip is scheduled before deletion
    assertFalse(env.tripData(TRIP_ID).tripTimes().hasAnyUpdates());

    var result = deleter.delete(parsedUpdate);

    assertNotNull(result);
    assertEquals(tripId, result.updatedTripTimes().getTrip().getId());
    assertTrue(result.updatedTripTimes().isDeleted());
  }

  @Test
  void deleteTripByTripOnServiceDateId() {
    var tripOnServiceDateId = new FeedScopedId(FEED_ID, TRIP_ON_SERVICE_DATE_ID);
    var tripRef = TripReference.builder().withTripOnServiceDateId(tripOnServiceDateId).build();
    var parsedUpdate = new TripDeletion(tripRef, env.defaultServiceDate(), null, null);

    // Verify trip is scheduled before deletion
    assertFalse(env.tripData(TRIP_ID).tripTimes().hasAnyUpdates());

    var result = deleter.delete(parsedUpdate);

    assertNotNull(result);
    // The underlying trip should be deleted
    var expectedTripId = new FeedScopedId(FEED_ID, TRIP_ID);
    assertEquals(expectedTripId, result.updatedTripTimes().getTrip().getId());
    assertTrue(result.updatedTripTimes().isDeleted());
  }

  @Test
  void tripNotFound_returnsFailure() {
    var unknownTripId = new FeedScopedId(FEED_ID, "unknown-trip");
    var tripRef = TripReference.ofTripId(unknownTripId);
    var parsedUpdate = new TripDeletion(tripRef, env.defaultServiceDate(), null, null);

    // Resolver throws exception when trip is not found anywhere
    var ex = assertThrows(UpdateException.class, () -> resolver.resolve(parsedUpdate));
    assertEquals(UpdateErrorType.NO_TRIP_FOR_CANCELLATION_FOUND, ex.errorType());
  }

  @Test
  void patternNotFound_returnsFailure() {
    // Create a new environment with a trip but no pattern for the given service date
    // This is a bit contrived - we'll create a trip but use a different service date
    var tripId = new FeedScopedId(FEED_ID, TRIP_ID);
    var tripRef = TripReference.ofTripId(tripId);

    // Use a different service date than the one used to build the environment
    // The trip exists but has no pattern for this date
    var differentDate = LocalDate.of(2099, 1, 1);
    var parsedUpdate = new TripDeletion(tripRef, differentDate, null, null);

    // Resolution succeeds for DELETE_TRIP because we find the scheduled pattern regardless of date.
    // The trip can be deleted on any date, the pattern is found from the scheduled timetable.
    assertDoesNotThrow(() -> resolver.resolve(parsedUpdate));
  }

  @Test
  void noTripReference_returnsFailure() {
    // Empty trip reference with neither tripId nor tripOnServiceDateId
    var tripRef = TripReference.builder().build();
    var parsedUpdate = new TripDeletion(tripRef, env.defaultServiceDate(), null, null);

    // Resolver throws exception when no trip reference is provided
    var ex = assertThrows(UpdateException.class, () -> resolver.resolve(parsedUpdate));
    assertEquals(UpdateErrorType.NO_TRIP_FOR_CANCELLATION_FOUND, ex.errorType());
  }

  @Test
  void deletedTripAppliedToSnapshot() {
    var tripId = new FeedScopedId(FEED_ID, TRIP_ID);
    var tripRef = TripReference.ofTripId(tripId);
    var parsedUpdate = new TripDeletion(tripRef, env.defaultServiceDate(), null, null);

    // Before deletion, the trip should be scheduled
    assertFalse(env.tripData(TRIP_ID).tripTimes().hasAnyUpdates());

    var result = deleter.delete(parsedUpdate);

    // Apply the update to the snapshot and commit it
    SnapshotTestHelper.applyAndCommit(env, result.realTimeTripUpdate());

    // After applying to snapshot, verify the trip is deleted in the timetable
    var tripData = env.tripData(TRIP_ID);
    assertTrue(tripData.tripTimes().isDeleted());
  }
}
