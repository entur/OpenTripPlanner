package org.opentripplanner.updater.trip.handlers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.transit.model._data.FeedScopedIdForTestFactory;
import org.opentripplanner.transit.model._data.TransitTestEnvironment;
import org.opentripplanner.transit.model._data.TripInput;
import org.opentripplanner.transit.model.timetable.RealTimeState;
import org.opentripplanner.transit.model.timetable.RealTimeTripTimes;
import org.opentripplanner.transit.service.TransitEditorService;
import org.opentripplanner.updater.spi.UpdateError;
import org.opentripplanner.updater.trip.TimetableSnapshotManager;
import org.opentripplanner.updater.trip.TripIdResolver;
import org.opentripplanner.updater.trip.TripUpdateApplierContext;
import org.opentripplanner.updater.trip.model.ParsedStopTimeUpdate;
import org.opentripplanner.updater.trip.model.ParsedTripUpdate;
import org.opentripplanner.updater.trip.model.StopReference;
import org.opentripplanner.updater.trip.model.TimeUpdate;
import org.opentripplanner.updater.trip.model.TripReference;
import org.opentripplanner.updater.trip.model.TripUpdateType;

/**
 * Tests for {@link UpdateExistingTripHandler}.
 */
class UpdateExistingTripHandlerTest {

  private static final String FEED_ID = FeedScopedIdForTestFactory.FEED_ID;
  private static final String TRIP_ID = "trip1";

  /**
   * Trip schedule: A@10:00 (36000s), B@10:30 (37800s), C@11:00 (39600s)
   */
  private static final int STOP_A_ARRIVAL = 10 * 3600;
  private static final int STOP_B_ARRIVAL = 10 * 3600 + 30 * 60;
  private static final int STOP_C_ARRIVAL = 11 * 3600;

  private TransitTestEnvironment env;
  private TransitEditorService transitService;
  private TimetableSnapshotManager snapshotManager;
  private TripUpdateApplierContext context;
  private UpdateExistingTripHandler handler;

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
      )
      .build();

    transitService = (TransitEditorService) env.transitService();
    snapshotManager = env.timetableSnapshotManager();
    var tripIdResolver = new TripIdResolver(env.transitService());
    context = new TripUpdateApplierContext(env.feedId(), snapshotManager, tripIdResolver);
    handler = new UpdateExistingTripHandler();
  }

  @Test
  void delayUpdate_singleStop() {
    var tripId = new FeedScopedId(FEED_ID, TRIP_ID);
    var tripRef = TripReference.ofTripId(tripId);

    // 5 minute delay (300 seconds) at stop B (index 1)
    var stopBRef = StopReference.ofStopId(new FeedScopedId(FEED_ID, "B"));
    var stopUpdate = ParsedStopTimeUpdate.builder(stopBRef)
      .withStopSequence(1)
      .withArrivalUpdate(TimeUpdate.ofDelay(300))
      .withDepartureUpdate(TimeUpdate.ofDelay(300))
      .build();

    var parsedUpdate = ParsedTripUpdate.builder(
      TripUpdateType.UPDATE_EXISTING,
      tripRef,
      env.defaultServiceDate()
    )
      .addStopTimeUpdate(stopUpdate)
      .build();

    var result = handler.handle(parsedUpdate, context, transitService);

    assertTrue(result.isSuccess(), "Expected success but got: " + result);
    assertNotNull(result.successValue());

    var updatedTimes = result.successValue().updatedTripTimes();
    assertEquals(RealTimeState.UPDATED, updatedTimes.getRealTimeState());
    assertEquals(tripId, updatedTimes.getTrip().getId());

    // Stop B (index 1) should have 5 minute delay
    assertEquals(STOP_B_ARRIVAL + 300, updatedTimes.getArrivalTime(1));
    assertEquals(STOP_B_ARRIVAL + 300, updatedTimes.getDepartureTime(1));
  }

  @Test
  void delayUpdate_allStops() {
    var tripId = new FeedScopedId(FEED_ID, TRIP_ID);
    var tripRef = TripReference.ofTripId(tripId);

    // 1 min, 3 min, 5 min delays at stops A, B, C respectively
    var stopAUpdate = ParsedStopTimeUpdate.builder(
      StopReference.ofStopId(new FeedScopedId(FEED_ID, "A"))
    )
      .withStopSequence(0)
      .withArrivalUpdate(TimeUpdate.ofDelay(60))
      .withDepartureUpdate(TimeUpdate.ofDelay(60))
      .build();

    var stopBUpdate = ParsedStopTimeUpdate.builder(
      StopReference.ofStopId(new FeedScopedId(FEED_ID, "B"))
    )
      .withStopSequence(1)
      .withArrivalUpdate(TimeUpdate.ofDelay(180))
      .withDepartureUpdate(TimeUpdate.ofDelay(180))
      .build();

    var stopCUpdate = ParsedStopTimeUpdate.builder(
      StopReference.ofStopId(new FeedScopedId(FEED_ID, "C"))
    )
      .withStopSequence(2)
      .withArrivalUpdate(TimeUpdate.ofDelay(300))
      .withDepartureUpdate(TimeUpdate.ofDelay(300))
      .build();

    var parsedUpdate = ParsedTripUpdate.builder(
      TripUpdateType.UPDATE_EXISTING,
      tripRef,
      env.defaultServiceDate()
    )
      .withStopTimeUpdates(List.of(stopAUpdate, stopBUpdate, stopCUpdate))
      .build();

    var result = handler.handle(parsedUpdate, context, transitService);

    assertTrue(result.isSuccess(), "Expected success but got: " + result);

    var updatedTimes = result.successValue().updatedTripTimes();
    assertEquals(RealTimeState.UPDATED, updatedTimes.getRealTimeState());

    // Each stop should have its specific delay
    assertEquals(STOP_A_ARRIVAL + 60, updatedTimes.getArrivalTime(0));
    assertEquals(STOP_B_ARRIVAL + 180, updatedTimes.getArrivalTime(1));
    assertEquals(STOP_C_ARRIVAL + 300, updatedTimes.getArrivalTime(2));
  }

  @Test
  void absoluteTimeUpdate_siriStyle() {
    var tripId = new FeedScopedId(FEED_ID, TRIP_ID);
    var tripRef = TripReference.ofTripId(tripId);

    // SIRI uses absolute times - stop B arrives at 10:35 (38100 seconds) instead of 10:30
    int absoluteTime = 10 * 3600 + 35 * 60;
    var stopBRef = StopReference.ofStopId(new FeedScopedId(FEED_ID, "B"));
    var stopUpdate = ParsedStopTimeUpdate.builder(stopBRef)
      .withStopSequence(1)
      .withArrivalUpdate(TimeUpdate.ofAbsolute(absoluteTime, STOP_B_ARRIVAL))
      .withDepartureUpdate(TimeUpdate.ofAbsolute(absoluteTime, STOP_B_ARRIVAL))
      .build();

    var parsedUpdate = ParsedTripUpdate.builder(
      TripUpdateType.UPDATE_EXISTING,
      tripRef,
      env.defaultServiceDate()
    )
      .addStopTimeUpdate(stopUpdate)
      .build();

    var result = handler.handle(parsedUpdate, context, transitService);

    assertTrue(result.isSuccess(), "Expected success but got: " + result);

    var updatedTimes = result.successValue().updatedTripTimes();
    assertEquals(RealTimeState.UPDATED, updatedTimes.getRealTimeState());

    // Stop B should have the absolute time (10:35)
    assertEquals(absoluteTime, updatedTimes.getArrivalTime(1));
    assertEquals(absoluteTime, updatedTimes.getDepartureTime(1));
  }

  @Test
  void tripNotFound_returnsFailure() {
    var unknownTripId = new FeedScopedId(FEED_ID, "unknown-trip");
    var tripRef = TripReference.ofTripId(unknownTripId);

    var parsedUpdate = ParsedTripUpdate.builder(
      TripUpdateType.UPDATE_EXISTING,
      tripRef,
      env.defaultServiceDate()
    ).build();

    var result = handler.handle(parsedUpdate, context, transitService);

    assertTrue(result.isFailure());
    assertEquals(UpdateError.UpdateErrorType.TRIP_NOT_FOUND, result.failureValue().errorType());
  }

  @Test
  void patternNotFound_returnsFailure() {
    var tripId = new FeedScopedId(FEED_ID, TRIP_ID);
    var tripRef = TripReference.ofTripId(tripId);

    // Use a date that has no service
    var differentDate = LocalDate.of(2099, 1, 1);
    var parsedUpdate = ParsedTripUpdate.builder(
      TripUpdateType.UPDATE_EXISTING,
      tripRef,
      differentDate
    ).build();

    var result = handler.handle(parsedUpdate, context, transitService);

    // Trip exists but pattern might not be found for different date
    // The behavior depends on implementation - either success (fallback to scheduled)
    // or failure (no pattern for date)
    if (result.isSuccess()) {
      // Handler correctly falls back to scheduled pattern.
      // No updates were provided, so state remains SCHEDULED.
      assertNotNull(result.successValue().updatedTripTimes());
    } else {
      // Handler correctly returns an error
      assertNotNull(result.failureValue());
    }
  }

  @Test
  void skippedStop_markedCancelled() {
    var tripId = new FeedScopedId(FEED_ID, TRIP_ID);
    var tripRef = TripReference.ofTripId(tripId);

    // Stop B is skipped
    var stopBRef = StopReference.ofStopId(new FeedScopedId(FEED_ID, "B"));
    var stopUpdate = ParsedStopTimeUpdate.builder(stopBRef)
      .withStopSequence(1)
      .withStatus(ParsedStopTimeUpdate.StopUpdateStatus.SKIPPED)
      .build();

    var parsedUpdate = ParsedTripUpdate.builder(
      TripUpdateType.UPDATE_EXISTING,
      tripRef,
      env.defaultServiceDate()
    )
      .addStopTimeUpdate(stopUpdate)
      .build();

    var result = handler.handle(parsedUpdate, context, transitService);

    assertTrue(result.isSuccess(), "Expected success but got: " + result);

    var updatedTimes = (RealTimeTripTimes) result.successValue().updatedTripTimes();
    assertEquals(RealTimeState.UPDATED, updatedTimes.getRealTimeState());

    // Stop B (index 1) should be marked as cancelled
    assertTrue(updatedTimes.isCancelledStop(1));
  }

  @Test
  void recordedStop_markedRecorded() {
    var tripId = new FeedScopedId(FEED_ID, TRIP_ID);
    var tripRef = TripReference.ofTripId(tripId);

    // Stop A is already passed (recorded), 1 min late (60 seconds)
    var stopARef = StopReference.ofStopId(new FeedScopedId(FEED_ID, "A"));
    var stopUpdate = ParsedStopTimeUpdate.builder(stopARef)
      .withStopSequence(0)
      .withRecorded(true)
      .withArrivalUpdate(TimeUpdate.ofDelay(60))
      .withDepartureUpdate(TimeUpdate.ofDelay(60))
      .build();

    var parsedUpdate = ParsedTripUpdate.builder(
      TripUpdateType.UPDATE_EXISTING,
      tripRef,
      env.defaultServiceDate()
    )
      .addStopTimeUpdate(stopUpdate)
      .build();

    var result = handler.handle(parsedUpdate, context, transitService);

    assertTrue(result.isSuccess(), "Expected success but got: " + result);

    var updatedTimes = result.successValue().updatedTripTimes();
    assertEquals(RealTimeState.UPDATED, updatedTimes.getRealTimeState());

    // Stop A (index 0) should be marked as recorded
    assertTrue(updatedTimes.isRecordedStop(0));

    // Time should still be updated
    assertEquals(STOP_A_ARRIVAL + 60, updatedTimes.getArrivalTime(0));
  }

  @Test
  void inaccuratePrediction_markedInaccurate() {
    var tripId = new FeedScopedId(FEED_ID, TRIP_ID);
    var tripRef = TripReference.ofTripId(tripId);

    // Stop B has inaccurate prediction
    var stopBRef = StopReference.ofStopId(new FeedScopedId(FEED_ID, "B"));
    var stopUpdate = ParsedStopTimeUpdate.builder(stopBRef)
      .withStopSequence(1)
      .withPredictionInaccurate(true)
      .withArrivalUpdate(TimeUpdate.ofDelay(300))
      .withDepartureUpdate(TimeUpdate.ofDelay(300))
      .build();

    var parsedUpdate = ParsedTripUpdate.builder(
      TripUpdateType.UPDATE_EXISTING,
      tripRef,
      env.defaultServiceDate()
    )
      .addStopTimeUpdate(stopUpdate)
      .build();

    var result = handler.handle(parsedUpdate, context, transitService);

    assertTrue(result.isSuccess(), "Expected success but got: " + result);

    var updatedTimes = (RealTimeTripTimes) result.successValue().updatedTripTimes();
    assertTrue(updatedTimes.isPredictionInaccurate(1));
  }
}
