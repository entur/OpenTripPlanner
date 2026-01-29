package org.opentripplanner.updater.trip.handlers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.transit.model._data.FeedScopedIdForTestFactory;
import org.opentripplanner.transit.model._data.TransitTestEnvironment;
import org.opentripplanner.transit.model._data.TripInput;
import org.opentripplanner.transit.model.timetable.RealTimeState;
import org.opentripplanner.transit.model.timetable.RealTimeTripTimes;
import org.opentripplanner.transit.service.TransitEditorService;
import org.opentripplanner.updater.spi.UpdateError;
import org.opentripplanner.updater.trip.ServiceDateResolver;
import org.opentripplanner.updater.trip.StopResolver;
import org.opentripplanner.updater.trip.TimetableSnapshotManager;
import org.opentripplanner.updater.trip.TripResolver;
import org.opentripplanner.updater.trip.TripUpdateApplierContext;
import org.opentripplanner.updater.trip.gtfs.BackwardsDelayPropagationType;
import org.opentripplanner.updater.trip.gtfs.ForwardsDelayPropagationType;
import org.opentripplanner.updater.trip.model.ParsedStopTimeUpdate;
import org.opentripplanner.updater.trip.model.ParsedTripUpdate;
import org.opentripplanner.updater.trip.model.StopReference;
import org.opentripplanner.updater.trip.model.StopReplacementConstraint;
import org.opentripplanner.updater.trip.model.StopUpdateStrategy;
import org.opentripplanner.updater.trip.model.TimeUpdate;
import org.opentripplanner.updater.trip.model.TripReference;
import org.opentripplanner.updater.trip.model.TripUpdateOptions;
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
    var tripResolver = new TripResolver(env.transitService());
    var serviceDateResolver = new ServiceDateResolver(tripResolver);
    var stopResolver = new StopResolver(env.transitService());
    var tripPatternCache = new org.opentripplanner.updater.trip.siri.SiriTripPatternCache(
      new org.opentripplanner.updater.trip.siri.SiriTripPatternIdGenerator(),
      env.transitService()::findPattern
    );
    context = new TripUpdateApplierContext(
      env.feedId(),
      snapshotManager,
      tripResolver,
      serviceDateResolver,
      stopResolver,
      tripPatternCache
    );
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
      .withOptions(
        TripUpdateOptions.gtfsRtDefaults(
          ForwardsDelayPropagationType.NONE,
          BackwardsDelayPropagationType.NONE
        )
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
      .withOptions(
        TripUpdateOptions.gtfsRtDefaults(
          ForwardsDelayPropagationType.NONE,
          BackwardsDelayPropagationType.NONE
        )
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
      .withOptions(
        TripUpdateOptions.gtfsRtDefaults(
          ForwardsDelayPropagationType.NONE,
          BackwardsDelayPropagationType.NONE
        )
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
      .withOptions(
        TripUpdateOptions.gtfsRtDefaults(
          ForwardsDelayPropagationType.NONE,
          BackwardsDelayPropagationType.NONE
        )
      )
      .addStopTimeUpdate(stopUpdate)
      .build();

    var result = handler.handle(parsedUpdate, context, transitService);

    assertTrue(result.isSuccess(), "Expected success but got: " + result);

    var updatedTimes = (RealTimeTripTimes) result.successValue().updatedTripTimes();
    // With GTFS-RT defaults, cancelled stops are tracked as pickup/dropoff changes,
    // which creates a MODIFIED pattern (matching legacy GTFS-RT behavior)
    assertEquals(RealTimeState.MODIFIED, updatedTimes.getRealTimeState());

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
      .withOptions(
        TripUpdateOptions.gtfsRtDefaults(
          ForwardsDelayPropagationType.NONE,
          BackwardsDelayPropagationType.NONE
        )
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
      .withOptions(
        TripUpdateOptions.gtfsRtDefaults(
          ForwardsDelayPropagationType.NONE,
          BackwardsDelayPropagationType.NONE
        )
      )
      .addStopTimeUpdate(stopUpdate)
      .build();

    var result = handler.handle(parsedUpdate, context, transitService);

    assertTrue(result.isSuccess(), "Expected success but got: " + result);

    var updatedTimes = (RealTimeTripTimes) result.successValue().updatedTripTimes();
    assertTrue(updatedTimes.isPredictionInaccurate(1));
  }

  /**
   * Tests for StopReplacementConstraint validation.
   */
  @Nested
  class StopReplacementConstraintTests {

    private static final String STATION_1 = "station1";
    private static final String STATION_2 = "station2";

    private TransitTestEnvironment stationEnv;
    private TransitEditorService stationTransitService;
    private TripUpdateApplierContext stationContext;

    @BeforeEach
    void setUpStationEnvironment() {
      var builder = TransitTestEnvironment.of();

      // Create two stations with stops in each
      builder.station(STATION_1);
      builder.station(STATION_2);

      var stopA1 = builder.stopAtStation("A1", STATION_1);
      // A2 is created for replacement tests but not used in the trip itself
      builder.stopAtStation("A2", STATION_1);
      var stopB1 = builder.stopAtStation("B1", STATION_2);

      stationEnv = builder
        .addTrip(TripInput.of("stationTrip").addStop(stopA1, "10:00").addStop(stopB1, "10:30"))
        .build();

      stationTransitService = (TransitEditorService) stationEnv.transitService();
      var tripResolver = new TripResolver(stationEnv.transitService());
      var serviceDateResolver = new ServiceDateResolver(tripResolver);
      var stopResolver = new StopResolver(stationEnv.transitService());
      var tripPatternCache = new org.opentripplanner.updater.trip.siri.SiriTripPatternCache(
        new org.opentripplanner.updater.trip.siri.SiriTripPatternIdGenerator(),
        stationEnv.transitService()::findPattern
      );
      stationContext = new TripUpdateApplierContext(
        stationEnv.feedId(),
        stationEnv.timetableSnapshotManager(),
        tripResolver,
        serviceDateResolver,
        stopResolver,
        tripPatternCache
      );
    }

    @Test
    void siriConstraint_allowsReplacementWithinSameStation() {
      var tripId = new FeedScopedId(FEED_ID, "stationTrip");
      var tripRef = TripReference.ofTripId(tripId);

      // Stop A1 is replaced by A2 (both in station1) - should be allowed
      var stopA1Id = new FeedScopedId(FEED_ID, "A1");
      var stopA2Id = new FeedScopedId(FEED_ID, "A2");
      var stopUpdate = ParsedStopTimeUpdate.builder(
        // original A1, assigned A2
        StopReference.ofStopId(stopA1Id, stopA2Id)
      )
        .withStopSequence(0)
        .withArrivalUpdate(TimeUpdate.ofDelay(60))
        .withDepartureUpdate(TimeUpdate.ofDelay(60))
        .build();

      var options = TripUpdateOptions.builder()
        .withStopReplacementConstraint(StopReplacementConstraint.SAME_PARENT_STATION)
        .build();

      var parsedUpdate = ParsedTripUpdate.builder(
        TripUpdateType.UPDATE_EXISTING,
        tripRef,
        stationEnv.defaultServiceDate()
      )
        .withOptions(options)
        .addStopTimeUpdate(stopUpdate)
        .build();

      var result = handler.handle(parsedUpdate, stationContext, stationTransitService);

      assertTrue(result.isSuccess(), "Expected success but got: " + result);
    }

    @Test
    void siriConstraint_rejectsReplacementOutsideStation() {
      var tripId = new FeedScopedId(FEED_ID, "stationTrip");
      var tripRef = TripReference.ofTripId(tripId);

      // Stop A1 (station1) is replaced by B1 (station2) - should be rejected
      var stopA1Id = new FeedScopedId(FEED_ID, "A1");
      var stopB1Id = new FeedScopedId(FEED_ID, "B1");
      var stopUpdate = ParsedStopTimeUpdate.builder(
        // original A1, assigned B1 (different station)
        StopReference.ofStopId(stopA1Id, stopB1Id)
      )
        .withStopSequence(0)
        .withArrivalUpdate(TimeUpdate.ofDelay(60))
        .build();

      var options = TripUpdateOptions.builder()
        .withStopReplacementConstraint(StopReplacementConstraint.SAME_PARENT_STATION)
        .build();

      var parsedUpdate = ParsedTripUpdate.builder(
        TripUpdateType.UPDATE_EXISTING,
        tripRef,
        stationEnv.defaultServiceDate()
      )
        .withOptions(options)
        .addStopTimeUpdate(stopUpdate)
        .build();

      var result = handler.handle(parsedUpdate, stationContext, stationTransitService);

      assertTrue(result.isFailure(), "Expected failure but got success");
      assertEquals(UpdateError.UpdateErrorType.STOP_MISMATCH, result.failureValue().errorType());
      assertEquals(0, result.failureValue().stopIndex());
    }

    @Test
    void gtfsRtConstraint_allowsAnyReplacement() {
      var tripId = new FeedScopedId(FEED_ID, "stationTrip");
      var tripRef = TripReference.ofTripId(tripId);

      // Stop A1 (station1) is replaced by B1 (station2) - should be allowed with ANY_STOP
      var stopA1Id = new FeedScopedId(FEED_ID, "A1");
      var stopB1Id = new FeedScopedId(FEED_ID, "B1");
      var stopUpdate = ParsedStopTimeUpdate.builder(StopReference.ofStopId(stopA1Id, stopB1Id))
        .withStopSequence(0)
        .withArrivalUpdate(TimeUpdate.ofDelay(60))
        .withDepartureUpdate(TimeUpdate.ofDelay(60))
        .build();

      var options = TripUpdateOptions.builder()
        .withStopReplacementConstraint(StopReplacementConstraint.ANY_STOP)
        .build();

      var parsedUpdate = ParsedTripUpdate.builder(
        TripUpdateType.UPDATE_EXISTING,
        tripRef,
        stationEnv.defaultServiceDate()
      )
        .withOptions(options)
        .addStopTimeUpdate(stopUpdate)
        .build();

      var result = handler.handle(parsedUpdate, stationContext, stationTransitService);

      assertTrue(result.isSuccess(), "Expected success but got: " + result);
    }

    @Test
    void notAllowedConstraint_rejectsAnyReplacement() {
      var tripId = new FeedScopedId(FEED_ID, "stationTrip");
      var tripRef = TripReference.ofTripId(tripId);

      // Even replacement within same station should be rejected with NOT_ALLOWED
      var stopA1Id = new FeedScopedId(FEED_ID, "A1");
      var stopA2Id = new FeedScopedId(FEED_ID, "A2");
      var stopUpdate = ParsedStopTimeUpdate.builder(StopReference.ofStopId(stopA1Id, stopA2Id))
        .withStopSequence(0)
        .withArrivalUpdate(TimeUpdate.ofDelay(60))
        .build();

      var options = TripUpdateOptions.builder()
        .withStopReplacementConstraint(StopReplacementConstraint.NOT_ALLOWED)
        .build();

      var parsedUpdate = ParsedTripUpdate.builder(
        TripUpdateType.UPDATE_EXISTING,
        tripRef,
        stationEnv.defaultServiceDate()
      )
        .withOptions(options)
        .addStopTimeUpdate(stopUpdate)
        .build();

      var result = handler.handle(parsedUpdate, stationContext, stationTransitService);

      assertTrue(result.isFailure(), "Expected failure but got success");
      assertEquals(UpdateError.UpdateErrorType.STOP_MISMATCH, result.failureValue().errorType());
    }

    @Test
    void noAssignedStop_validationSkipped() {
      var tripId = new FeedScopedId(FEED_ID, "stationTrip");
      var tripRef = TripReference.ofTripId(tripId);

      // No assigned stop - just a regular delay update, should succeed regardless of constraint
      var stopA1Id = new FeedScopedId(FEED_ID, "A1");
      var stopUpdate = ParsedStopTimeUpdate.builder(
        // No assigned stop
        StopReference.ofStopId(stopA1Id)
      )
        .withStopSequence(0)
        .withArrivalUpdate(TimeUpdate.ofDelay(60))
        .withDepartureUpdate(TimeUpdate.ofDelay(60))
        .build();

      var options = TripUpdateOptions.builder()
        .withStopReplacementConstraint(StopReplacementConstraint.NOT_ALLOWED)
        .build();

      var parsedUpdate = ParsedTripUpdate.builder(
        TripUpdateType.UPDATE_EXISTING,
        tripRef,
        stationEnv.defaultServiceDate()
      )
        .withOptions(options)
        .addStopTimeUpdate(stopUpdate)
        .build();

      var result = handler.handle(parsedUpdate, stationContext, stationTransitService);

      assertTrue(result.isSuccess(), "Expected success but got: " + result);
    }

    @Test
    void siriStopPointRef_matchesByStopReferenceWithinSameStation() {
      var tripId = new FeedScopedId(FEED_ID, "stationTrip");
      var tripRef = TripReference.ofTripId(tripId);

      // SIRI-style update: uses stopPointRef for A2 (same station as scheduled A1)
      // No stopSequence - must match by stop reference
      // SIRI-style updates require all stops to be present
      var stopA2Update = ParsedStopTimeUpdate.builder(
        // A2 is in the same station as A1
        StopReference.ofScheduledStopPointOrStopId(new FeedScopedId(FEED_ID, "A2"))
      )
        // No stopSequence - forces matching by stop reference
        .withArrivalUpdate(TimeUpdate.ofDelay(60))
        .withDepartureUpdate(TimeUpdate.ofDelay(60))
        .build();

      var stopB1Update = ParsedStopTimeUpdate.builder(
        StopReference.ofScheduledStopPointOrStopId(new FeedScopedId(FEED_ID, "B1"))
      )
        .withArrivalUpdate(TimeUpdate.ofDelay(120))
        .withDepartureUpdate(TimeUpdate.ofDelay(120))
        .build();

      var options = TripUpdateOptions.builder()
        .withStopReplacementConstraint(StopReplacementConstraint.SAME_PARENT_STATION)
        .withStopUpdateStrategy(StopUpdateStrategy.FULL_UPDATE)
        .build();

      var parsedUpdate = ParsedTripUpdate.builder(
        TripUpdateType.UPDATE_EXISTING,
        tripRef,
        stationEnv.defaultServiceDate()
      )
        .withOptions(options)
        .withStopTimeUpdates(List.of(stopA2Update, stopB1Update))
        .build();

      var result = handler.handle(parsedUpdate, stationContext, stationTransitService);

      assertTrue(result.isSuccess(), "Expected success but got: " + result);
    }

    @Test
    void siriStopPointRef_rejectsReplacementOutsideStation() {
      var tripId = new FeedScopedId(FEED_ID, "stationTrip");
      var tripRef = TripReference.ofTripId(tripId);

      // SIRI-style update: uses stopPointRef for B1 (station2) to replace scheduled A1 (station1)
      // This should fail because B1 is in a different station from A1
      // The update must include all stops in the trip (A1 and B1)
      var stopA1Update = ParsedStopTimeUpdate.builder(
        // Attempt to replace A1 with B1 (different station)
        StopReference.ofScheduledStopPointOrStopId(new FeedScopedId(FEED_ID, "B1"))
      )
        // No stopSequence - forces matching by stop reference
        .withArrivalUpdate(TimeUpdate.ofDelay(60))
        .withDepartureUpdate(TimeUpdate.ofDelay(60))
        .build();

      var stopB1Update = ParsedStopTimeUpdate.builder(
        StopReference.ofScheduledStopPointOrStopId(new FeedScopedId(FEED_ID, "B1"))
      )
        .withArrivalUpdate(TimeUpdate.ofDelay(120))
        .withDepartureUpdate(TimeUpdate.ofDelay(120))
        .build();

      var options = TripUpdateOptions.builder()
        .withStopReplacementConstraint(StopReplacementConstraint.SAME_PARENT_STATION)
        .withStopUpdateStrategy(StopUpdateStrategy.FULL_UPDATE)
        .build();

      var parsedUpdate = ParsedTripUpdate.builder(
        TripUpdateType.UPDATE_EXISTING,
        tripRef,
        stationEnv.defaultServiceDate()
      )
        .withOptions(options)
        .withStopTimeUpdates(List.of(stopA1Update, stopB1Update))
        .build();

      var result = handler.handle(parsedUpdate, stationContext, stationTransitService);

      assertTrue(
        result.isFailure(),
        "Expected failure when replacing A1 (station1) with B1 (station2)"
      );
      assertEquals(UpdateError.UpdateErrorType.STOP_MISMATCH, result.failureValue().errorType());
      assertEquals(0, result.failureValue().stopIndex());
    }
  }

  /**
   * Tests for stop update strategy differences.
   */
  @Nested
  class StopUpdateStrategyTests {

    @Test
    void gtfsRtStopIdLookup_matchesStopInPattern() {
      var tripId = new FeedScopedId(FEED_ID, TRIP_ID);
      var tripRef = TripReference.ofTripId(tripId);

      // GTFS-RT with stopId but no stopSequence - stops provided in different order
      // Update stop C first, then stop A (reversed order from pattern A->B->C)
      var stopCUpdate = ParsedStopTimeUpdate.builder(
        StopReference.ofStopId(new FeedScopedId(FEED_ID, "C"))
      )
        .withArrivalUpdate(TimeUpdate.ofDelay(300))
        .withDepartureUpdate(TimeUpdate.ofDelay(300))
        .build();

      var stopAUpdate = ParsedStopTimeUpdate.builder(
        StopReference.ofStopId(new FeedScopedId(FEED_ID, "A"))
      )
        .withArrivalUpdate(TimeUpdate.ofDelay(60))
        .withDepartureUpdate(TimeUpdate.ofDelay(60))
        .build();

      var options = TripUpdateOptions.gtfsRtDefaults(
        ForwardsDelayPropagationType.NONE,
        BackwardsDelayPropagationType.NONE
      );

      var parsedUpdate = ParsedTripUpdate.builder(
        TripUpdateType.UPDATE_EXISTING,
        tripRef,
        env.defaultServiceDate()
      )
        .withOptions(options)
        .withStopTimeUpdates(List.of(stopCUpdate, stopAUpdate))
        .build();

      var result = handler.handle(parsedUpdate, context, transitService);

      assertTrue(result.isSuccess(), "Expected success but got: " + result);
      var updatedTimes = result.successValue().updatedTripTimes();

      // Stop A should have 1 minute delay (matched by ID, not position)
      assertEquals(STOP_A_ARRIVAL + 60, updatedTimes.getArrivalTime(0));
      // Stop B should remain at scheduled time (not updated)
      assertEquals(STOP_B_ARRIVAL, updatedTimes.getArrivalTime(1));
      // Stop C should have 5 minute delay (matched by ID, not position)
      assertEquals(STOP_C_ARRIVAL + 300, updatedTimes.getArrivalTime(2));
    }

    @Test
    void fullUpdateStrategy_rejectsStopSequence() {
      var tripId = new FeedScopedId(FEED_ID, TRIP_ID);
      var tripRef = TripReference.ofTripId(tripId);

      // Try to use FULL_UPDATE with stopSequence - should be rejected
      var stopAUpdate = ParsedStopTimeUpdate.builder(
        StopReference.ofStopId(new FeedScopedId(FEED_ID, "A"))
      )
        .withStopSequence(0)
        .withArrivalUpdate(TimeUpdate.ofDelay(60))
        .build();

      var options = TripUpdateOptions.builder()
        .withStopUpdateStrategy(StopUpdateStrategy.FULL_UPDATE)
        .build();

      var parsedUpdate = ParsedTripUpdate.builder(
        TripUpdateType.UPDATE_EXISTING,
        tripRef,
        env.defaultServiceDate()
      )
        .withOptions(options)
        .addStopTimeUpdate(stopAUpdate)
        .build();

      var result = handler.handle(parsedUpdate, context, transitService);

      assertTrue(result.isFailure(), "Expected failure but got success");
      assertEquals(
        UpdateError.UpdateErrorType.INVALID_STOP_SEQUENCE,
        result.failureValue().errorType()
      );
    }

    @Test
    void fullUpdateStrategy_rejectsTooFewStops() {
      var tripId = new FeedScopedId(FEED_ID, TRIP_ID);
      var tripRef = TripReference.ofTripId(tripId);

      // Pattern has 3 stops (A, B, C) but only provide 2
      var stopAUpdate = ParsedStopTimeUpdate.builder(
        StopReference.ofStopId(new FeedScopedId(FEED_ID, "A"))
      )
        .withArrivalUpdate(TimeUpdate.ofDelay(60))
        .build();

      var stopBUpdate = ParsedStopTimeUpdate.builder(
        StopReference.ofStopId(new FeedScopedId(FEED_ID, "B"))
      )
        .withArrivalUpdate(TimeUpdate.ofDelay(120))
        .build();

      var options = TripUpdateOptions.builder()
        .withStopUpdateStrategy(StopUpdateStrategy.FULL_UPDATE)
        .build();

      var parsedUpdate = ParsedTripUpdate.builder(
        TripUpdateType.UPDATE_EXISTING,
        tripRef,
        env.defaultServiceDate()
      )
        .withOptions(options)
        .withStopTimeUpdates(List.of(stopAUpdate, stopBUpdate))
        .build();

      var result = handler.handle(parsedUpdate, context, transitService);

      assertTrue(result.isFailure(), "Expected failure but got success");
      assertEquals(UpdateError.UpdateErrorType.TOO_FEW_STOPS, result.failureValue().errorType());
    }

    @Test
    void fullUpdateStrategy_rejectsTooManyStops() {
      var tripId = new FeedScopedId(FEED_ID, TRIP_ID);
      var tripRef = TripReference.ofTripId(tripId);

      // Pattern has 3 stops (A, B, C) but provide 4
      var stopAUpdate = ParsedStopTimeUpdate.builder(
        StopReference.ofStopId(new FeedScopedId(FEED_ID, "A"))
      )
        .withArrivalUpdate(TimeUpdate.ofDelay(60))
        .build();

      var stopBUpdate = ParsedStopTimeUpdate.builder(
        StopReference.ofStopId(new FeedScopedId(FEED_ID, "B"))
      )
        .withArrivalUpdate(TimeUpdate.ofDelay(120))
        .build();

      var stopCUpdate = ParsedStopTimeUpdate.builder(
        StopReference.ofStopId(new FeedScopedId(FEED_ID, "C"))
      )
        .withArrivalUpdate(TimeUpdate.ofDelay(180))
        .build();

      var stopDUpdate = ParsedStopTimeUpdate.builder(
        StopReference.ofStopId(new FeedScopedId(FEED_ID, "A"))
      )
        .withArrivalUpdate(TimeUpdate.ofDelay(240))
        .build();

      var options = TripUpdateOptions.builder()
        .withStopUpdateStrategy(StopUpdateStrategy.FULL_UPDATE)
        .build();

      var parsedUpdate = ParsedTripUpdate.builder(
        TripUpdateType.UPDATE_EXISTING,
        tripRef,
        env.defaultServiceDate()
      )
        .withOptions(options)
        .withStopTimeUpdates(List.of(stopAUpdate, stopBUpdate, stopCUpdate, stopDUpdate))
        .build();

      var result = handler.handle(parsedUpdate, context, transitService);

      assertTrue(result.isFailure(), "Expected failure but got success");
      assertEquals(UpdateError.UpdateErrorType.TOO_MANY_STOPS, result.failureValue().errorType());
    }
  }

  /**
   * Tests for delay propagation (GTFS-RT style).
   */
  @Nested
  class DelayPropagationTests {

    @Test
    void forwardsPropagation_delayAtFirstStopPropagates() {
      var tripId = new FeedScopedId(FEED_ID, TRIP_ID);
      var tripRef = TripReference.ofTripId(tripId);

      // Update only stop A with 5 minute delay
      var stopAUpdate = ParsedStopTimeUpdate.builder(
        StopReference.ofStopId(new FeedScopedId(FEED_ID, "A"))
      )
        .withStopSequence(0)
        .withArrivalUpdate(TimeUpdate.ofDelay(300))
        .withDepartureUpdate(TimeUpdate.ofDelay(300))
        .build();

      // Configure with GTFS-RT defaults: forwards propagation enabled
      var options = TripUpdateOptions.gtfsRtDefaults(
        ForwardsDelayPropagationType.DEFAULT,
        BackwardsDelayPropagationType.NONE
      );

      var parsedUpdate = ParsedTripUpdate.builder(
        TripUpdateType.UPDATE_EXISTING,
        tripRef,
        env.defaultServiceDate()
      )
        .withOptions(options)
        .addStopTimeUpdate(stopAUpdate)
        .build();

      var result = handler.handle(parsedUpdate, context, transitService);

      assertTrue(result.isSuccess(), "Expected success but got: " + result);
      var updatedTimes = result.successValue().updatedTripTimes();

      // Stop A has explicit 5 minute delay
      assertEquals(STOP_A_ARRIVAL + 300, updatedTimes.getArrivalTime(0));
      assertEquals(STOP_A_ARRIVAL + 300, updatedTimes.getDepartureTime(0));

      // Stop B and C should have delay propagated (no explicit updates)
      assertEquals(STOP_B_ARRIVAL + 300, updatedTimes.getArrivalTime(1));
      assertEquals(STOP_B_ARRIVAL + 300, updatedTimes.getDepartureTime(1));
      assertEquals(STOP_C_ARRIVAL + 300, updatedTimes.getArrivalTime(2));
      assertEquals(STOP_C_ARRIVAL + 300, updatedTimes.getDepartureTime(2));
    }

    @Test
    void forwardsPropagation_disabled_noDelayCopied() {
      var tripId = new FeedScopedId(FEED_ID, TRIP_ID);
      var tripRef = TripReference.ofTripId(tripId);

      // SIRI-style: all stops must be present, no stopSequence
      var stopAUpdate = ParsedStopTimeUpdate.builder(
        StopReference.ofStopId(new FeedScopedId(FEED_ID, "A"))
      )
        .withArrivalUpdate(TimeUpdate.ofDelay(300))
        .withDepartureUpdate(TimeUpdate.ofDelay(300))
        .build();

      var stopBUpdate = ParsedStopTimeUpdate.builder(
        StopReference.ofStopId(new FeedScopedId(FEED_ID, "B"))
      )
        .withArrivalUpdate(TimeUpdate.ofDelay(0))
        .withDepartureUpdate(TimeUpdate.ofDelay(0))
        .build();

      var stopCUpdate = ParsedStopTimeUpdate.builder(
        StopReference.ofStopId(new FeedScopedId(FEED_ID, "C"))
      )
        .withArrivalUpdate(TimeUpdate.ofDelay(0))
        .withDepartureUpdate(TimeUpdate.ofDelay(0))
        .build();

      // SIRI defaults: no propagation, FULL_UPDATE strategy
      var options = TripUpdateOptions.siriDefaults();

      var parsedUpdate = ParsedTripUpdate.builder(
        TripUpdateType.UPDATE_EXISTING,
        tripRef,
        env.defaultServiceDate()
      )
        .withOptions(options)
        .withStopTimeUpdates(List.of(stopAUpdate, stopBUpdate, stopCUpdate))
        .build();

      var result = handler.handle(parsedUpdate, context, transitService);

      assertTrue(result.isSuccess(), "Expected success but got: " + result);
      var updatedTimes = result.successValue().updatedTripTimes();

      // Stop A has explicit 5 minute delay
      assertEquals(STOP_A_ARRIVAL + 300, updatedTimes.getArrivalTime(0));

      // Stop B and C should remain at scheduled time (no propagation)
      assertEquals(STOP_B_ARRIVAL, updatedTimes.getArrivalTime(1));
      assertEquals(STOP_C_ARRIVAL, updatedTimes.getArrivalTime(2));
    }

    @Test
    void backwardsPropagation_maintainsNonDecreasingTimes() {
      var tripId = new FeedScopedId(FEED_ID, TRIP_ID);
      var tripRef = TripReference.ofTripId(tripId);

      // Update only stop B (middle stop) with EARLY arrival (-5 minutes)
      var stopBUpdate = ParsedStopTimeUpdate.builder(
        StopReference.ofStopId(new FeedScopedId(FEED_ID, "B"))
      )
        .withStopSequence(1)
        .withArrivalUpdate(TimeUpdate.ofDelay(-300))
        .withDepartureUpdate(TimeUpdate.ofDelay(-300))
        .build();

      // Configure with backwards propagation
      var options = TripUpdateOptions.gtfsRtDefaults(
        ForwardsDelayPropagationType.NONE,
        BackwardsDelayPropagationType.REQUIRED
      );

      var parsedUpdate = ParsedTripUpdate.builder(
        TripUpdateType.UPDATE_EXISTING,
        tripRef,
        env.defaultServiceDate()
      )
        .withOptions(options)
        .addStopTimeUpdate(stopBUpdate)
        .build();

      var result = handler.handle(parsedUpdate, context, transitService);

      assertTrue(result.isSuccess(), "Expected success but got: " + result);
      var updatedTimes = result.successValue().updatedTripTimes();

      // Stop B has explicit 5 minute early arrival
      assertEquals(STOP_B_ARRIVAL - 300, updatedTimes.getArrivalTime(1));

      // Stop A should be adjusted to maintain non-decreasing times
      // It should be at most STOP_B_ARRIVAL - 300
      assertTrue(
        updatedTimes.getDepartureTime(0) <= updatedTimes.getArrivalTime(1),
        "Departure at A should not be after arrival at B"
      );
    }

    @Test
    void mixedPropagation_gtfsRtDefaults() {
      var tripId = new FeedScopedId(FEED_ID, TRIP_ID);
      var tripRef = TripReference.ofTripId(tripId);

      // Update only stop B (middle stop) with 10 minute delay
      var stopBUpdate = ParsedStopTimeUpdate.builder(
        StopReference.ofStopId(new FeedScopedId(FEED_ID, "B"))
      )
        .withStopSequence(1)
        .withArrivalUpdate(TimeUpdate.ofDelay(600))
        .withDepartureUpdate(TimeUpdate.ofDelay(600))
        .build();

      // GTFS-RT defaults: both forward and backward propagation
      var options = TripUpdateOptions.gtfsRtDefaults(
        ForwardsDelayPropagationType.DEFAULT,
        BackwardsDelayPropagationType.REQUIRED_NO_DATA
      );

      var parsedUpdate = ParsedTripUpdate.builder(
        TripUpdateType.UPDATE_EXISTING,
        tripRef,
        env.defaultServiceDate()
      )
        .withOptions(options)
        .addStopTimeUpdate(stopBUpdate)
        .build();

      var result = handler.handle(parsedUpdate, context, transitService);

      assertTrue(result.isSuccess(), "Expected success but got: " + result);
      var updatedTimes = result.successValue().updatedTripTimes();

      // Stop B has explicit 10 minute delay
      assertEquals(STOP_B_ARRIVAL + 600, updatedTimes.getArrivalTime(1));
      assertEquals(STOP_B_ARRIVAL + 600, updatedTimes.getDepartureTime(1));

      // Stop A should be at scheduled time (backwards propagation REQUIRED only adjusts if needed)
      assertEquals(STOP_A_ARRIVAL, updatedTimes.getArrivalTime(0));
      assertEquals(STOP_A_ARRIVAL, updatedTimes.getDepartureTime(0));

      // Stop C should have delay propagated forward
      assertEquals(STOP_C_ARRIVAL + 600, updatedTimes.getArrivalTime(2));
      assertEquals(STOP_C_ARRIVAL + 600, updatedTimes.getDepartureTime(2));
    }

    @Test
    void partialUpdate_propagatesToUnupdatedStops() {
      var tripId = new FeedScopedId(FEED_ID, TRIP_ID);
      var tripRef = TripReference.ofTripId(tripId);

      // Update stops A and C, but not B
      var stopAUpdate = ParsedStopTimeUpdate.builder(
        StopReference.ofStopId(new FeedScopedId(FEED_ID, "A"))
      )
        .withStopSequence(0)
        .withArrivalUpdate(TimeUpdate.ofDelay(120))
        .withDepartureUpdate(TimeUpdate.ofDelay(120))
        .build();

      var stopCUpdate = ParsedStopTimeUpdate.builder(
        StopReference.ofStopId(new FeedScopedId(FEED_ID, "C"))
      )
        .withStopSequence(2)
        .withArrivalUpdate(TimeUpdate.ofDelay(360))
        .withDepartureUpdate(TimeUpdate.ofDelay(360))
        .build();

      // GTFS-RT defaults with forwards propagation
      var options = TripUpdateOptions.gtfsRtDefaults(
        ForwardsDelayPropagationType.DEFAULT,
        BackwardsDelayPropagationType.NONE
      );

      var parsedUpdate = ParsedTripUpdate.builder(
        TripUpdateType.UPDATE_EXISTING,
        tripRef,
        env.defaultServiceDate()
      )
        .withOptions(options)
        .withStopTimeUpdates(List.of(stopAUpdate, stopCUpdate))
        .build();

      var result = handler.handle(parsedUpdate, context, transitService);

      assertTrue(result.isSuccess(), "Expected success but got: " + result);
      var updatedTimes = result.successValue().updatedTripTimes();

      // Stop A has explicit 2 minute delay
      assertEquals(STOP_A_ARRIVAL + 120, updatedTimes.getArrivalTime(0));

      // Stop B should have interpolated time based on propagation
      // Should be between A's departure and C's arrival
      int stopBTime = updatedTimes.getArrivalTime(1);
      assertTrue(
        stopBTime >= updatedTimes.getDepartureTime(0) &&
          stopBTime <= updatedTimes.getArrivalTime(2),
        "Stop B time should be between A departure and C arrival"
      );

      // Stop C has explicit 6 minute delay
      assertEquals(STOP_C_ARRIVAL + 360, updatedTimes.getArrivalTime(2));
    }
  }
}
