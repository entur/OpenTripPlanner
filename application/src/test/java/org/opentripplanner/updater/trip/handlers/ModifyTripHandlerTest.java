package org.opentripplanner.updater.trip.handlers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.opentripplanner.core.model.i18n.I18NString;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.transit.model._data.FeedScopedIdForTestFactory;
import org.opentripplanner.transit.model._data.TransitTestEnvironment;
import org.opentripplanner.transit.model._data.TripInput;
import org.opentripplanner.transit.model.framework.Result;
import org.opentripplanner.transit.model.timetable.RealTimeState;
import org.opentripplanner.transit.service.TransitEditorService;
import org.opentripplanner.updater.spi.UpdateError;
import org.opentripplanner.updater.trip.ExistingTripResolver;
import org.opentripplanner.updater.trip.ServiceDateResolver;
import org.opentripplanner.updater.trip.StopResolver;
import org.opentripplanner.updater.trip.TimetableSnapshotManager;
import org.opentripplanner.updater.trip.TripResolver;
import org.opentripplanner.updater.trip.gtfs.BackwardsDelayPropagationType;
import org.opentripplanner.updater.trip.gtfs.ForwardsDelayPropagationType;
import org.opentripplanner.updater.trip.model.ParsedStopTimeUpdate;
import org.opentripplanner.updater.trip.model.ParsedTripUpdate;
import org.opentripplanner.updater.trip.model.ResolvedExistingTrip;
import org.opentripplanner.updater.trip.model.StopReference;
import org.opentripplanner.updater.trip.model.TimeUpdate;
import org.opentripplanner.updater.trip.model.TripReference;
import org.opentripplanner.updater.trip.model.TripUpdateOptions;
import org.opentripplanner.updater.trip.model.TripUpdateType;

/**
 * Tests for {@link ModifyTripHandler}.
 * <p>
 * This handler processes MODIFY_TRIP updates which include:
 * <ul>
 *   <li>GTFS-RT REPLACEMENT: Complete stop pattern replacement</li>
 *   <li>SIRI-ET EXTRA_CALL: Adding extra stops to an existing trip</li>
 * </ul>
 */
class ModifyTripHandlerTest {

  private static final ZoneId TIME_ZONE = ZoneId.of("America/New_York");
  private static final String FEED_ID = FeedScopedIdForTestFactory.FEED_ID;
  private static final String TRIP_ID = "trip1";

  /**
   * Tests for GTFS-RT REPLACEMENT trips.
   * REPLACEMENT allows complete freedom to define a new stop pattern.
   */
  @Nested
  class GtfsRtReplacementTests {

    private TransitTestEnvironment env;
    private TransitEditorService transitService;
    private TimetableSnapshotManager snapshotManager;
    private ExistingTripResolver resolver;
    private ModifyTripHandler handler;

    @BeforeEach
    void setUp() {
      var builder = TransitTestEnvironment.of().addStops("A", "B", "C", "D");

      var stopA = builder.stop("A");
      var stopB = builder.stop("B");
      var stopC = builder.stop("C");

      env = builder
        .addTrip(
          TripInput.of(TRIP_ID)
            .addStop(stopA, "10:00")
            .addStop(stopB, "10:30")
            .addStop(stopC, "11:00")
            .withHeadsign(I18NString.of("Original Headsign"))
        )
        .build();

      transitService = (TransitEditorService) env.transitService();
      snapshotManager = env.timetableSnapshotManager();
      var tripResolver = new TripResolver(env.transitService());
      var serviceDateResolver = new ServiceDateResolver(tripResolver, env.transitService());
      var stopResolver = new StopResolver(env.transitService());
      var tripPatternCache = new org.opentripplanner.updater.trip.siri.SiriTripPatternCache(
        new org.opentripplanner.updater.trip.siri.SiriTripPatternIdGenerator(),
        env.transitService()::findPattern
      );
      resolver = new ExistingTripResolver(
        transitService,
        tripResolver,
        serviceDateResolver,
        stopResolver,
        null,
        TIME_ZONE
      );
      handler = new ModifyTripHandler(snapshotManager, tripPatternCache);
    }

    private ResolvedExistingTrip resolve(ParsedTripUpdate parsedUpdate) {
      var result = resolver.resolve(parsedUpdate);
      if (result.isFailure()) {
        throw new IllegalStateException("Failed to resolve update: " + result.failureValue());
      }
      return result.successValue();
    }

    private Result<ResolvedExistingTrip, UpdateError> resolveForTest(
      ParsedTripUpdate parsedUpdate
    ) {
      return resolver.resolve(parsedUpdate);
    }

    @Test
    void replacementTrip_addStop() {
      // Original trip: A -> B -> C
      // Replacement: A -> B -> D -> C (adds stop D between B and C)
      var tripId = new FeedScopedId(FEED_ID, TRIP_ID);
      var tripRef = TripReference.ofTripId(tripId);

      var parsedUpdate = ParsedTripUpdate.builder(
        TripUpdateType.MODIFY_TRIP,
        tripRef,
        env.defaultServiceDate()
      )
        .withOptions(
          TripUpdateOptions.gtfsRtDefaults(
            ForwardsDelayPropagationType.NONE,
            BackwardsDelayPropagationType.NONE
          )
        )
        .addStopTimeUpdate(createStopUpdate("A", 0, 10 * 3600))
        .addStopTimeUpdate(createStopUpdate("B", 1, 10 * 3600 + 30 * 60))
        .addStopTimeUpdate(createStopUpdate("D", 2, 10 * 3600 + 45 * 60))
        .addStopTimeUpdate(createStopUpdate("C", 3, 11 * 3600))
        .build();

      var result = handler.handle(resolve(parsedUpdate), transitService);

      assertTrue(result.isSuccess(), "Expected success but got: " + result);

      var update = result.successValue();
      assertNotNull(update);
      assertEquals(RealTimeState.MODIFIED, update.updatedTripTimes().getRealTimeState());

      // New pattern should have 4 stops
      assertEquals(4, update.pattern().numberOfStops());

      // Verify stop order: A, B, D, C
      assertEquals("A", update.pattern().getStop(0).getId().getId());
      assertEquals("B", update.pattern().getStop(1).getId().getId());
      assertEquals("D", update.pattern().getStop(2).getId().getId());
      assertEquals("C", update.pattern().getStop(3).getId().getId());
    }

    @Test
    void replacementTrip_removeStop() {
      // Original trip: A -> B -> C
      // Replacement: A -> C (removes stop B)
      var tripId = new FeedScopedId(FEED_ID, TRIP_ID);
      var tripRef = TripReference.ofTripId(tripId);

      var parsedUpdate = ParsedTripUpdate.builder(
        TripUpdateType.MODIFY_TRIP,
        tripRef,
        env.defaultServiceDate()
      )
        .withOptions(
          TripUpdateOptions.gtfsRtDefaults(
            ForwardsDelayPropagationType.NONE,
            BackwardsDelayPropagationType.NONE
          )
        )
        .addStopTimeUpdate(createStopUpdate("A", 0, 10 * 3600))
        .addStopTimeUpdate(createStopUpdate("C", 1, 11 * 3600))
        .build();

      var result = handler.handle(resolve(parsedUpdate), transitService);

      assertTrue(result.isSuccess(), "Expected success but got: " + result);

      var update = result.successValue();
      assertEquals(RealTimeState.MODIFIED, update.updatedTripTimes().getRealTimeState());

      // New pattern should have 2 stops
      assertEquals(2, update.pattern().numberOfStops());
      assertEquals("A", update.pattern().getStop(0).getId().getId());
      assertEquals("C", update.pattern().getStop(1).getId().getId());
    }

    @Test
    void replacementTrip_withHeadsignChange() {
      var tripId = new FeedScopedId(FEED_ID, TRIP_ID);
      var tripRef = TripReference.ofTripId(tripId);

      var stopAUpdate = ParsedStopTimeUpdate.builder(
        StopReference.ofStopId(new FeedScopedId(FEED_ID, "A"))
      )
        .withStopSequence(0)
        .withArrivalUpdate(TimeUpdate.ofAbsolute(10 * 3600, null))
        .withDepartureUpdate(TimeUpdate.ofAbsolute(10 * 3600, null))
        .withStopHeadsign(I18NString.of("New Headsign"))
        .build();

      var stopCUpdate = createStopUpdate("C", 1, 11 * 3600);

      var parsedUpdate = ParsedTripUpdate.builder(
        TripUpdateType.MODIFY_TRIP,
        tripRef,
        env.defaultServiceDate()
      )
        .withOptions(
          TripUpdateOptions.gtfsRtDefaults(
            ForwardsDelayPropagationType.NONE,
            BackwardsDelayPropagationType.NONE
          )
        )
        .addStopTimeUpdate(stopAUpdate)
        .addStopTimeUpdate(stopCUpdate)
        .build();

      var result = handler.handle(resolve(parsedUpdate), transitService);

      assertTrue(result.isSuccess(), "Expected success but got: " + result);

      var tripTimes = result.successValue().updatedTripTimes();
      assertEquals(I18NString.of("New Headsign"), tripTimes.getHeadsign(0));
    }

    @Test
    void replacementTrip_originalTripMarkedDeleted() {
      var tripId = new FeedScopedId(FEED_ID, TRIP_ID);
      var tripRef = TripReference.ofTripId(tripId);

      var parsedUpdate = ParsedTripUpdate.builder(
        TripUpdateType.MODIFY_TRIP,
        tripRef,
        env.defaultServiceDate()
      )
        .withOptions(
          TripUpdateOptions.gtfsRtDefaults(
            ForwardsDelayPropagationType.NONE,
            BackwardsDelayPropagationType.NONE
          )
        )
        .addStopTimeUpdate(createStopUpdate("A", 0, 10 * 3600))
        .addStopTimeUpdate(createStopUpdate("D", 1, 10 * 3600 + 30 * 60))
        .addStopTimeUpdate(createStopUpdate("C", 2, 11 * 3600))
        .build();

      var result = handler.handle(resolve(parsedUpdate), transitService);
      assertTrue(result.isSuccess());

      // Apply the update to the snapshot manager
      snapshotManager.updateBuffer(result.successValue().realTimeTripUpdate());
      snapshotManager.purgeAndCommit();

      // Get the original pattern and verify the trip is DELETED there
      var trip = transitService.getTrip(tripId);
      var originalPattern = transitService.findPattern(trip);
      var snapshot = snapshotManager.getTimetableSnapshot();
      var originalTimetable = snapshot.resolve(originalPattern, env.defaultServiceDate());
      var originalTripTimes = originalTimetable.getTripTimes(tripId);

      assertNotNull(originalTripTimes);
      assertEquals(RealTimeState.DELETED, originalTripTimes.getRealTimeState());
    }

    @Test
    void replacementTrip_tripNotFound() {
      var unknownTripId = new FeedScopedId(FEED_ID, "unknown-trip");
      var tripRef = TripReference.ofTripId(unknownTripId);

      var parsedUpdate = ParsedTripUpdate.builder(
        TripUpdateType.MODIFY_TRIP,
        tripRef,
        env.defaultServiceDate()
      )
        .withOptions(
          TripUpdateOptions.gtfsRtDefaults(
            ForwardsDelayPropagationType.NONE,
            BackwardsDelayPropagationType.NONE
          )
        )
        .addStopTimeUpdate(createStopUpdate("A", 0, 10 * 3600))
        .addStopTimeUpdate(createStopUpdate("C", 1, 11 * 3600))
        .build();

      // Resolution should fail because trip not found
      var resolveResult = resolveForTest(parsedUpdate);
      assertTrue(resolveResult.isFailure());
      assertEquals(
        UpdateError.UpdateErrorType.TRIP_NOT_FOUND,
        resolveResult.failureValue().errorType()
      );
    }

    @Test
    void replacementTrip_invalidServiceDate() {
      var tripId = new FeedScopedId(FEED_ID, TRIP_ID);
      var tripRef = TripReference.ofTripId(tripId);

      // Use a date far in the future where the trip doesn't operate
      var invalidDate = LocalDate.of(2099, 1, 1);

      var parsedUpdate = ParsedTripUpdate.builder(TripUpdateType.MODIFY_TRIP, tripRef, invalidDate)
        .withOptions(
          TripUpdateOptions.gtfsRtDefaults(
            ForwardsDelayPropagationType.NONE,
            BackwardsDelayPropagationType.NONE
          )
        )
        .addStopTimeUpdate(createStopUpdate("A", 0, 10 * 3600))
        .addStopTimeUpdate(createStopUpdate("C", 1, 11 * 3600))
        .build();

      // Resolution should fail because trip not running on this date
      var resolveResult = resolveForTest(parsedUpdate);
      assertTrue(resolveResult.isFailure());
      assertEquals(
        UpdateError.UpdateErrorType.NO_SERVICE_ON_DATE,
        resolveResult.failureValue().errorType()
      );
    }

    @Test
    void replacementTrip_unknownStop() {
      var tripId = new FeedScopedId(FEED_ID, TRIP_ID);
      var tripRef = TripReference.ofTripId(tripId);

      var parsedUpdate = ParsedTripUpdate.builder(
        TripUpdateType.MODIFY_TRIP,
        tripRef,
        env.defaultServiceDate()
      )
        .withOptions(
          TripUpdateOptions.gtfsRtDefaults(
            ForwardsDelayPropagationType.NONE,
            BackwardsDelayPropagationType.NONE
          )
        )
        .addStopTimeUpdate(createStopUpdate("A", 0, 10 * 3600))
        .addStopTimeUpdate(createStopUpdate("UNKNOWN_STOP", 1, 10 * 3600 + 30 * 60))
        .addStopTimeUpdate(createStopUpdate("C", 2, 11 * 3600))
        .build();

      var result = handler.handle(resolve(parsedUpdate), transitService);

      assertTrue(result.isFailure());
      assertEquals(UpdateError.UpdateErrorType.UNKNOWN_STOP, result.failureValue().errorType());
    }

    @Test
    void replacementTrip_tooFewStops() {
      var tripId = new FeedScopedId(FEED_ID, TRIP_ID);
      var tripRef = TripReference.ofTripId(tripId);

      // Only 1 stop - need at least 2
      var parsedUpdate = ParsedTripUpdate.builder(
        TripUpdateType.MODIFY_TRIP,
        tripRef,
        env.defaultServiceDate()
      )
        .withOptions(
          TripUpdateOptions.gtfsRtDefaults(
            ForwardsDelayPropagationType.NONE,
            BackwardsDelayPropagationType.NONE
          )
        )
        .addStopTimeUpdate(createStopUpdate("A", 0, 10 * 3600))
        .build();

      var result = handler.handle(resolve(parsedUpdate), transitService);

      assertTrue(result.isFailure());
      assertEquals(UpdateError.UpdateErrorType.TOO_FEW_STOPS, result.failureValue().errorType());
    }

    private ParsedStopTimeUpdate createStopUpdate(String stopId, int sequence, int timeSeconds) {
      return ParsedStopTimeUpdate.builder(StopReference.ofStopId(new FeedScopedId(FEED_ID, stopId)))
        .withStopSequence(sequence)
        .withArrivalUpdate(TimeUpdate.ofAbsolute(timeSeconds, null))
        .withDepartureUpdate(TimeUpdate.ofAbsolute(timeSeconds, null))
        .build();
    }
  }

  /**
   * Tests for SIRI-ET EXTRA_CALL trips.
   * EXTRA_CALL allows inserting new stops but non-extra stops must match the original pattern.
   */
  @Nested
  class SiriExtraCallTests {

    private static final String STATION_1 = "station1";
    private static final String STATION_2 = "station2";

    private TransitTestEnvironment env;
    private TransitEditorService transitService;
    private TimetableSnapshotManager snapshotManager;
    private ExistingTripResolver resolver;
    private ModifyTripHandler handler;

    @BeforeEach
    void setUp() {
      var builder = TransitTestEnvironment.of();

      // Create stations with stops
      builder.station(STATION_1);
      builder.station(STATION_2);
      // Station 3 is for stop D, which is a different station than B
      builder.station("station3");

      var stopA = builder.stopAtStation("A", STATION_1);
      var stopB = builder.stopAtStation("B", STATION_2);
      // Extra stops that can be inserted
      builder.stopAtStation("A2", STATION_1);
      // D is in station3 (different from B in station2) - used to test stop mismatch
      builder.stopAtStation("D", "station3");

      env = builder
        .addTrip(TripInput.of(TRIP_ID).addStop(stopA, "10:00").addStop(stopB, "10:30"))
        .build();

      transitService = (TransitEditorService) env.transitService();
      snapshotManager = env.timetableSnapshotManager();
      var tripResolver = new TripResolver(env.transitService());
      var serviceDateResolver = new ServiceDateResolver(tripResolver, env.transitService());
      var stopResolver = new StopResolver(env.transitService());
      var tripPatternCache = new org.opentripplanner.updater.trip.siri.SiriTripPatternCache(
        new org.opentripplanner.updater.trip.siri.SiriTripPatternIdGenerator(),
        env.transitService()::findPattern
      );
      resolver = new ExistingTripResolver(
        transitService,
        tripResolver,
        serviceDateResolver,
        stopResolver,
        null,
        TIME_ZONE
      );
      handler = new ModifyTripHandler(snapshotManager, tripPatternCache);
    }

    private ResolvedExistingTrip resolve(ParsedTripUpdate parsedUpdate) {
      var result = resolver.resolve(parsedUpdate);
      if (result.isFailure()) {
        throw new IllegalStateException("Failed to resolve update: " + result.failureValue());
      }
      return result.successValue();
    }

    @Test
    void extraCall_insertStop() {
      // Original trip: A -> B
      // With extra call: A -> D (extra) -> B
      var tripId = new FeedScopedId(FEED_ID, TRIP_ID);
      var tripRef = TripReference.ofTripId(tripId);

      var stopAUpdate = createSiriStopUpdate("A", 10 * 3600, false);
      // D is an extra call inserted between A and B
      var stopDUpdate = createSiriStopUpdate("D", 10 * 3600 + 15 * 60, true);
      var stopBUpdate = createSiriStopUpdate("B", 10 * 3600 + 30 * 60, false);

      var parsedUpdate = ParsedTripUpdate.builder(
        TripUpdateType.MODIFY_TRIP,
        tripRef,
        env.defaultServiceDate()
      )
        .withOptions(TripUpdateOptions.siriDefaults())
        .addStopTimeUpdate(stopAUpdate)
        .addStopTimeUpdate(stopDUpdate)
        .addStopTimeUpdate(stopBUpdate)
        .build();

      var result = handler.handle(resolve(parsedUpdate), transitService);

      assertTrue(result.isSuccess(), "Expected success but got: " + result);

      var update = result.successValue();
      assertEquals(RealTimeState.MODIFIED, update.updatedTripTimes().getRealTimeState());

      // New pattern should have 3 stops: A, D, B
      assertEquals(3, update.pattern().numberOfStops());
      assertEquals("A", update.pattern().getStop(0).getId().getId());
      assertEquals("D", update.pattern().getStop(1).getId().getId());
      assertEquals("B", update.pattern().getStop(2).getId().getId());
    }

    @Test
    void extraCall_sameStationReplacement() {
      // Original trip: A -> B
      // A replaced by A2 (same station) + extra call D
      var tripId = new FeedScopedId(FEED_ID, TRIP_ID);
      var tripRef = TripReference.ofTripId(tripId);

      // A2 replaces A (both in station1) - this should be allowed
      var stopA2Update = createSiriStopUpdate("A2", 10 * 3600, false);
      var stopDUpdate = createSiriStopUpdate("D", 10 * 3600 + 15 * 60, true);
      var stopBUpdate = createSiriStopUpdate("B", 10 * 3600 + 30 * 60, false);

      var parsedUpdate = ParsedTripUpdate.builder(
        TripUpdateType.MODIFY_TRIP,
        tripRef,
        env.defaultServiceDate()
      )
        .withOptions(TripUpdateOptions.siriDefaults())
        .addStopTimeUpdate(stopA2Update)
        .addStopTimeUpdate(stopDUpdate)
        .addStopTimeUpdate(stopBUpdate)
        .build();

      var result = handler.handle(resolve(parsedUpdate), transitService);

      assertTrue(result.isSuccess(), "Expected success but got: " + result);

      // Verify A2 is in the pattern, not A
      assertEquals("A2", result.successValue().pattern().getStop(0).getId().getId());
    }

    @Test
    void extraCall_wrongNumberOfNonExtraStops() {
      // Original trip: A -> B (2 stops)
      // Update has 1 non-extra stop + 1 extra = invalid
      var tripId = new FeedScopedId(FEED_ID, TRIP_ID);
      var tripRef = TripReference.ofTripId(tripId);

      var stopAUpdate = createSiriStopUpdate("A", 10 * 3600, false);
      var stopDUpdate = createSiriStopUpdate("D", 10 * 3600 + 15 * 60, true);
      // Missing stopB!

      var parsedUpdate = ParsedTripUpdate.builder(
        TripUpdateType.MODIFY_TRIP,
        tripRef,
        env.defaultServiceDate()
      )
        .withOptions(TripUpdateOptions.siriDefaults())
        .addStopTimeUpdate(stopAUpdate)
        .addStopTimeUpdate(stopDUpdate)
        .build();

      var result = handler.handle(resolve(parsedUpdate), transitService);

      assertTrue(result.isFailure());
      assertEquals(
        UpdateError.UpdateErrorType.INVALID_STOP_SEQUENCE,
        result.failureValue().errorType()
      );
    }

    @Test
    void extraCall_nonExtraStopDoesNotMatch() {
      // Original trip: A -> B
      // Update: A -> D (extra) -> D (not extra but should be B)
      var tripId = new FeedScopedId(FEED_ID, TRIP_ID);
      var tripRef = TripReference.ofTripId(tripId);

      var stopAUpdate = createSiriStopUpdate("A", 10 * 3600, false);
      var stopDExtraUpdate = createSiriStopUpdate("D", 10 * 3600 + 15 * 60, true);
      // D is used as non-extra stop but should be B
      var stopDNonExtraUpdate = createSiriStopUpdate("D", 10 * 3600 + 30 * 60, false);

      var parsedUpdate = ParsedTripUpdate.builder(
        TripUpdateType.MODIFY_TRIP,
        tripRef,
        env.defaultServiceDate()
      )
        .withOptions(TripUpdateOptions.siriDefaults())
        .addStopTimeUpdate(stopAUpdate)
        .addStopTimeUpdate(stopDExtraUpdate)
        .addStopTimeUpdate(stopDNonExtraUpdate)
        .build();

      var result = handler.handle(resolve(parsedUpdate), transitService);

      assertTrue(result.isFailure());
      assertEquals(UpdateError.UpdateErrorType.STOP_MISMATCH, result.failureValue().errorType());
    }

    private ParsedStopTimeUpdate createSiriStopUpdate(
      String stopId,
      int timeSeconds,
      boolean isExtraCall
    ) {
      return ParsedStopTimeUpdate.builder(StopReference.ofStopId(new FeedScopedId(FEED_ID, stopId)))
        .withArrivalUpdate(TimeUpdate.ofAbsolute(timeSeconds, null))
        .withDepartureUpdate(TimeUpdate.ofAbsolute(timeSeconds, null))
        .withIsExtraCall(isExtraCall)
        .build();
    }
  }
}
