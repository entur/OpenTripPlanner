package org.opentripplanner.updater.trip;

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
import org.opentripplanner.transit.model.timetable.RealTimeState;
import org.opentripplanner.transit.service.TransitEditorService;
import org.opentripplanner.updater.spi.UpdateError;
import org.opentripplanner.updater.trip.model.ParsedTripUpdate;
import org.opentripplanner.updater.trip.model.TripReference;
import org.opentripplanner.updater.trip.model.TripUpdateType;

/**
 * Tests for DefaultTripUpdateApplier.
 */
class DefaultTripUpdateApplierTest {

  private static final String FEED_ID = FeedScopedIdForTestFactory.FEED_ID;
  private static final String TRIP_ID = "trip1";
  private static final String TRIP_ON_SERVICE_DATE_ID = "dated-trip1";
  private static final ZoneId TIME_ZONE = ZoneId.of("America/New_York");

  private TransitTestEnvironment env;
  private DefaultTripUpdateApplier applier;
  private TransitEditorService transitService;
  private TimetableSnapshotManager snapshotManager;
  private TripUpdateApplierContext context;

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
    applier = new DefaultTripUpdateApplier(transitService);
    var tripResolver = new TripResolver(env.transitService());
    var serviceDateResolver = new ServiceDateResolver(tripResolver, env.transitService());
    var stopResolver = new StopResolver(env.transitService());
    var tripPatternCache = new org.opentripplanner.updater.trip.siri.SiriTripPatternCache(
      new org.opentripplanner.updater.trip.siri.SiriTripPatternIdGenerator(),
      env.transitService()::findPattern
    );
    context = new TripUpdateApplierContext(
      env.feedId(),
      TIME_ZONE,
      snapshotManager,
      tripResolver,
      serviceDateResolver,
      stopResolver,
      tripPatternCache
    );
  }

  @Test
  void testUpdateExisting_tripNotFound() {
    // When no trip ID is provided, the resolver returns TRIP_NOT_FOUND
    var update = ParsedTripUpdate.builder(
      TripUpdateType.UPDATE_EXISTING,
      TripReference.builder().build(),
      LocalDate.now()
    ).build();

    var result = applier.apply(update, context);

    assertTrue(result.isFailure());
    assertEquals(UpdateError.UpdateErrorType.TRIP_NOT_FOUND, result.failureValue().errorType());
  }

  @Test
  void testCancelTrip_tripNotFound() {
    // Empty trip reference should result in TRIP_NOT_FOUND
    var update = ParsedTripUpdate.builder(
      TripUpdateType.CANCEL_TRIP,
      TripReference.builder().build(),
      LocalDate.now()
    ).build();

    var result = applier.apply(update, context);

    assertTrue(result.isFailure());
    assertEquals(
      UpdateError.UpdateErrorType.NO_TRIP_FOR_CANCELLATION_FOUND,
      result.failureValue().errorType()
    );
  }

  @Test
  void testDeleteTrip_tripNotFound() {
    var update = ParsedTripUpdate.builder(
      TripUpdateType.DELETE_TRIP,
      TripReference.builder().build(),
      LocalDate.now()
    ).build();

    var result = applier.apply(update, context);

    assertTrue(result.isFailure());
    assertEquals(
      UpdateError.UpdateErrorType.NO_TRIP_FOR_CANCELLATION_FOUND,
      result.failureValue().errorType()
    );
  }

  @Test
  void testAddNewTrip_notImplemented() {
    var update = ParsedTripUpdate.builder(
      TripUpdateType.ADD_NEW_TRIP,
      TripReference.builder().build(),
      LocalDate.now()
    ).build();

    var result = applier.apply(update, context);

    assertTrue(result.isFailure());
    assertEquals(UpdateError.UpdateErrorType.UNKNOWN, result.failureValue().errorType());
  }

  @Test
  void testDeleteTrip_byTripId_success() {
    var tripId = new FeedScopedId(FEED_ID, TRIP_ID);
    var tripRef = TripReference.ofTripId(tripId);
    var update = ParsedTripUpdate.builder(
      TripUpdateType.DELETE_TRIP,
      tripRef,
      env.defaultServiceDate()
    ).build();

    // Verify trip is scheduled before deletion
    assertEquals(RealTimeState.SCHEDULED, env.tripData(TRIP_ID).realTimeState());

    var result = applier.apply(update, context);

    assertTrue(result.isSuccess());
    assertNotNull(result.successValue());
    assertEquals(tripId, result.successValue().updatedTripTimes().getTrip().getId());
    assertEquals(
      RealTimeState.DELETED,
      result.successValue().updatedTripTimes().getRealTimeState()
    );
  }

  @Test
  void testDeleteTrip_byTripOnServiceDateId_success() {
    var tripOnServiceDateId = new FeedScopedId(FEED_ID, TRIP_ON_SERVICE_DATE_ID);
    var tripRef = TripReference.builder().withTripOnServiceDateId(tripOnServiceDateId).build();
    var update = ParsedTripUpdate.builder(
      TripUpdateType.DELETE_TRIP,
      tripRef,
      env.defaultServiceDate()
    ).build();

    // Verify trip is scheduled before deletion
    assertEquals(RealTimeState.SCHEDULED, env.tripData(TRIP_ID).realTimeState());

    var result = applier.apply(update, context);

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
  void testDeleteTrip_appliedToSnapshot() {
    var tripId = new FeedScopedId(FEED_ID, TRIP_ID);
    var tripRef = TripReference.ofTripId(tripId);
    var update = ParsedTripUpdate.builder(
      TripUpdateType.DELETE_TRIP,
      tripRef,
      env.defaultServiceDate()
    ).build();

    // Before deletion, the trip should be scheduled
    assertEquals(RealTimeState.SCHEDULED, env.tripData(TRIP_ID).realTimeState());

    var result = applier.apply(update, context);

    assertTrue(result.isSuccess());

    // Apply the update to the snapshot manager
    var updateResult = result.successValue();
    snapshotManager.updateBuffer(updateResult.realTimeTripUpdate());

    // Commit the snapshot to make the update visible
    snapshotManager.purgeAndCommit();

    // After applying to snapshot, verify the trip is deleted in the timetable
    var tripData = env.tripData(TRIP_ID);
    assertEquals(RealTimeState.DELETED, tripData.realTimeState());
  }

  @Test
  void testCancelTrip_byTripId_success() {
    var tripId = new FeedScopedId(FEED_ID, TRIP_ID);
    var tripRef = TripReference.ofTripId(tripId);
    var update = ParsedTripUpdate.builder(
      TripUpdateType.CANCEL_TRIP,
      tripRef,
      env.defaultServiceDate()
    ).build();

    // Verify trip is scheduled before cancellation
    assertEquals(RealTimeState.SCHEDULED, env.tripData(TRIP_ID).realTimeState());

    var result = applier.apply(update, context);

    assertTrue(result.isSuccess());
    assertNotNull(result.successValue());
    assertEquals(tripId, result.successValue().updatedTripTimes().getTrip().getId());
    assertEquals(
      RealTimeState.CANCELED,
      result.successValue().updatedTripTimes().getRealTimeState()
    );
  }

  @Test
  void testCancelTrip_byTripOnServiceDateId_success() {
    var tripOnServiceDateId = new FeedScopedId(FEED_ID, TRIP_ON_SERVICE_DATE_ID);
    var tripRef = TripReference.builder().withTripOnServiceDateId(tripOnServiceDateId).build();
    var update = ParsedTripUpdate.builder(
      TripUpdateType.CANCEL_TRIP,
      tripRef,
      env.defaultServiceDate()
    ).build();

    // Verify trip is scheduled before cancellation
    assertEquals(RealTimeState.SCHEDULED, env.tripData(TRIP_ID).realTimeState());

    var result = applier.apply(update, context);

    assertTrue(result.isSuccess());
    assertNotNull(result.successValue());
    // The underlying trip should be cancelled
    var expectedTripId = new FeedScopedId(FEED_ID, TRIP_ID);
    assertEquals(expectedTripId, result.successValue().updatedTripTimes().getTrip().getId());
    assertEquals(
      RealTimeState.CANCELED,
      result.successValue().updatedTripTimes().getRealTimeState()
    );
  }

  @Test
  void testCancelTrip_appliedToSnapshot() {
    var tripId = new FeedScopedId(FEED_ID, TRIP_ID);
    var tripRef = TripReference.ofTripId(tripId);
    var update = ParsedTripUpdate.builder(
      TripUpdateType.CANCEL_TRIP,
      tripRef,
      env.defaultServiceDate()
    ).build();

    // Before cancellation, the trip should be scheduled
    assertEquals(RealTimeState.SCHEDULED, env.tripData(TRIP_ID).realTimeState());

    var result = applier.apply(update, context);

    assertTrue(result.isSuccess());

    // Apply the update to the snapshot manager
    var updateResult = result.successValue();
    snapshotManager.updateBuffer(updateResult.realTimeTripUpdate());

    // Commit the snapshot to make the update visible
    snapshotManager.purgeAndCommit();

    // After applying to snapshot, verify the trip is cancelled in the timetable
    var tripData = env.tripData(TRIP_ID);
    assertEquals(RealTimeState.CANCELED, tripData.realTimeState());
  }
}
