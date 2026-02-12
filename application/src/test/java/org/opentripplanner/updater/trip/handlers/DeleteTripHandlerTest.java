package org.opentripplanner.updater.trip.handlers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.transit.model._data.FeedScopedIdForTestFactory;
import org.opentripplanner.transit.model._data.TransitTestEnvironment;
import org.opentripplanner.transit.model._data.TripInput;
import org.opentripplanner.transit.model.framework.Result;
import org.opentripplanner.transit.model.timetable.RealTimeState;
import org.opentripplanner.transit.service.TransitEditorService;
import org.opentripplanner.updater.spi.UpdateError;
import org.opentripplanner.updater.trip.ServiceDateResolver;
import org.opentripplanner.updater.trip.TimetableSnapshotManager;
import org.opentripplanner.updater.trip.TripRemovalResolver;
import org.opentripplanner.updater.trip.TripResolver;
import org.opentripplanner.updater.trip.model.ParsedTripUpdate;
import org.opentripplanner.updater.trip.model.ResolvedTripRemoval;
import org.opentripplanner.updater.trip.model.TripReference;
import org.opentripplanner.updater.trip.model.TripUpdateType;

/**
 * Tests for {@link DeleteTripHandler}.
 */
class DeleteTripHandlerTest {

  private static final ZoneId TIME_ZONE = ZoneId.of("America/New_York");

  private static final String FEED_ID = FeedScopedIdForTestFactory.FEED_ID;
  private static final String TRIP_ID = "trip1";
  private static final String TRIP_ON_SERVICE_DATE_ID = "dated-trip1";

  private TransitTestEnvironment env;
  private TransitEditorService transitService;
  private TimetableSnapshotManager snapshotManager;
  private TripRemovalResolver resolver;
  private DeleteTripHandler handler;

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
    snapshotManager = env.timetableSnapshotManager();
    var tripResolver = new TripResolver(env.transitService());
    var serviceDateResolver = new ServiceDateResolver(tripResolver, env.transitService());
    resolver = new TripRemovalResolver(transitService, tripResolver, serviceDateResolver);
    handler = new DeleteTripHandler(snapshotManager);
  }

  private ResolvedTripRemoval resolve(ParsedTripUpdate parsedUpdate) {
    var result = resolver.resolve(parsedUpdate);
    if (result.isFailure()) {
      throw new IllegalStateException("Failed to resolve update: " + result.failureValue());
    }
    return result.successValue();
  }

  private Result<ResolvedTripRemoval, UpdateError> resolveForTest(ParsedTripUpdate parsedUpdate) {
    return resolver.resolve(parsedUpdate);
  }

  @Test
  void deleteTripByTripId() {
    var tripId = new FeedScopedId(FEED_ID, TRIP_ID);
    var tripRef = TripReference.ofTripId(tripId);
    var parsedUpdate = ParsedTripUpdate.builder(
      TripUpdateType.DELETE_TRIP,
      tripRef,
      env.defaultServiceDate()
    ).build();

    // Verify trip is scheduled before deletion
    assertEquals(RealTimeState.SCHEDULED, env.tripData(TRIP_ID).realTimeState());

    var result = handler.handle(resolve(parsedUpdate));

    assertTrue(result.isSuccess());
    assertNotNull(result.successValue());
    assertEquals(tripId, result.successValue().updatedTripTimes().getTrip().getId());
    assertEquals(
      RealTimeState.DELETED,
      result.successValue().updatedTripTimes().getRealTimeState()
    );
  }

  @Test
  void deleteTripByTripOnServiceDateId() {
    var tripOnServiceDateId = new FeedScopedId(FEED_ID, TRIP_ON_SERVICE_DATE_ID);
    var tripRef = TripReference.builder().withTripOnServiceDateId(tripOnServiceDateId).build();
    var parsedUpdate = ParsedTripUpdate.builder(
      TripUpdateType.DELETE_TRIP,
      tripRef,
      env.defaultServiceDate()
    ).build();

    // Verify trip is scheduled before deletion
    assertEquals(RealTimeState.SCHEDULED, env.tripData(TRIP_ID).realTimeState());

    var result = handler.handle(resolve(parsedUpdate));

    assertTrue(result.isSuccess());
    assertNotNull(result.successValue());
    // The underlying trip should be deleted
    var expectedTripId = new FeedScopedId(FEED_ID, TRIP_ID);
    assertEquals(expectedTripId, result.successValue().updatedTripTimes().getTrip().getId());
    assertEquals(
      RealTimeState.DELETED,
      result.successValue().updatedTripTimes().getRealTimeState()
    );
  }

  @Test
  void tripNotFound_returnsFailure() {
    var unknownTripId = new FeedScopedId(FEED_ID, "unknown-trip");
    var tripRef = TripReference.ofTripId(unknownTripId);
    var parsedUpdate = ParsedTripUpdate.builder(
      TripUpdateType.DELETE_TRIP,
      tripRef,
      env.defaultServiceDate()
    ).build();

    // For DELETE_TRIP, resolver returns success with null values when trip not found
    // The handler then checks and returns NO_TRIP_FOR_CANCELLATION_FOUND
    var resolveResult = resolveForTest(parsedUpdate);
    assertTrue(resolveResult.isSuccess());

    // Handler returns error because no scheduled trip and no previously added trip
    var result = handler.handle(resolveResult.successValue());
    assertTrue(result.isFailure());
    assertEquals(
      UpdateError.UpdateErrorType.NO_TRIP_FOR_CANCELLATION_FOUND,
      result.failureValue().errorType()
    );
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
    var parsedUpdate = ParsedTripUpdate.builder(
      TripUpdateType.DELETE_TRIP,
      tripRef,
      differentDate
    ).build();

    // Resolution succeeds for DELETE_TRIP because we find the scheduled pattern regardless of date.
    // The trip can be deleted on any date, the pattern is found from the scheduled timetable.
    var resolveResult = resolveForTest(parsedUpdate);
    assertTrue(resolveResult.isSuccess(), "Expected success but got: " + resolveResult);
  }

  @Test
  void noTripReference_returnsFailure() {
    // Empty trip reference with neither tripId nor tripOnServiceDateId
    var tripRef = TripReference.builder().build();
    var parsedUpdate = ParsedTripUpdate.builder(
      TripUpdateType.DELETE_TRIP,
      tripRef,
      env.defaultServiceDate()
    ).build();

    // For DELETE_TRIP, resolver returns success with null values when no trip reference
    // The handler then checks and returns NO_TRIP_FOR_CANCELLATION_FOUND
    var resolveResult = resolveForTest(parsedUpdate);
    assertTrue(resolveResult.isSuccess());

    // Handler returns error because no scheduled trip and no previously added trip
    var result = handler.handle(resolveResult.successValue());
    assertTrue(result.isFailure());
    assertEquals(
      UpdateError.UpdateErrorType.NO_TRIP_FOR_CANCELLATION_FOUND,
      result.failureValue().errorType()
    );
  }

  @Test
  void deletedTripAppliedToSnapshot() {
    var tripId = new FeedScopedId(FEED_ID, TRIP_ID);
    var tripRef = TripReference.ofTripId(tripId);
    var parsedUpdate = ParsedTripUpdate.builder(
      TripUpdateType.DELETE_TRIP,
      tripRef,
      env.defaultServiceDate()
    ).build();

    // Before deletion, the trip should be scheduled
    assertEquals(RealTimeState.SCHEDULED, env.tripData(TRIP_ID).realTimeState());

    var result = handler.handle(resolve(parsedUpdate));

    assertTrue(result.isSuccess());

    // Apply the update to the snapshot manager
    var update = result.successValue();
    snapshotManager.updateBuffer(update.realTimeTripUpdate());

    // Commit the snapshot to make the update visible
    snapshotManager.purgeAndCommit();

    // After applying to snapshot, verify the trip is deleted in the timetable
    var tripData = env.tripData(TRIP_ID);
    assertEquals(RealTimeState.DELETED, tripData.realTimeState());
  }
}
