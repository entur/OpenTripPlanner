package org.opentripplanner.updater.trip;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.transit.model._data.FeedScopedIdForTestFactory;
import org.opentripplanner.transit.model._data.TransitTestEnvironment;
import org.opentripplanner.transit.model._data.TripInput;
import org.opentripplanner.transit.model.framework.Deduplicator;
import org.opentripplanner.transit.model.timetable.RealTimeState;
import org.opentripplanner.transit.service.TransitEditorService;
import org.opentripplanner.updater.spi.UpdateErrorType;
import org.opentripplanner.updater.spi.UpdateException;
import org.opentripplanner.updater.trip.handlers.GtfsRtRouteCreationStrategy;
import org.opentripplanner.updater.trip.model.ParsedCancelTrip;
import org.opentripplanner.updater.trip.model.ParsedDeleteTrip;
import org.opentripplanner.updater.trip.model.ParsedUpdateExisting;
import org.opentripplanner.updater.trip.model.TripReference;
import org.opentripplanner.updater.trip.patterncache.TripPatternCache;
import org.opentripplanner.updater.trip.patterncache.TripPatternIdGenerator;

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
    var tripPatternCache = new TripPatternCache(
      new TripPatternIdGenerator(),
      env.transitService()::findPattern
    );
    applier = new DefaultTripUpdateApplier(
      env.feedId(),
      TIME_ZONE,
      transitService,
      new Deduplicator(),
      snapshotManager,
      tripPatternCache,
      null,
      new GtfsRtRouteCreationStrategy(env.feedId(), null)
    );
  }

  @Test
  void testUpdateExisting_tripNotFound() {
    var update = ParsedUpdateExisting.builder(
      TripReference.builder().build(),
      LocalDate.now()
    ).build();

    var exception = assertThrows(UpdateException.class, () -> applier.apply(update));
    assertEquals(UpdateErrorType.TRIP_NOT_FOUND, exception.errorType());
  }

  @Test
  void testCancelTrip_tripNotFound() {
    var update = new ParsedCancelTrip(TripReference.builder().build(), LocalDate.now(), null, null);

    var exception = assertThrows(UpdateException.class, () -> applier.apply(update));
    assertEquals(UpdateErrorType.NO_TRIP_FOR_CANCELLATION_FOUND, exception.errorType());
  }

  @Test
  void testDeleteTrip_tripNotFound() {
    var update = new ParsedDeleteTrip(TripReference.builder().build(), LocalDate.now(), null, null);

    var exception = assertThrows(UpdateException.class, () -> applier.apply(update));
    assertEquals(UpdateErrorType.NO_TRIP_FOR_CANCELLATION_FOUND, exception.errorType());
  }

  @Test
  void testDeleteTrip_byTripId_success() {
    var tripId = new FeedScopedId(FEED_ID, TRIP_ID);
    var tripRef = TripReference.ofTripId(tripId);
    var update = new ParsedDeleteTrip(tripRef, env.defaultServiceDate(), null, null);

    assertEquals(RealTimeState.SCHEDULED, env.tripData(TRIP_ID).realTimeState());

    var result = applier.apply(update);

    assertNotNull(result);
    assertEquals(tripId, result.updatedTripTimes().getTrip().getId());
    assertEquals(RealTimeState.DELETED, result.updatedTripTimes().getRealTimeState());
  }

  @Test
  void testDeleteTrip_byTripOnServiceDateId_success() {
    var tripOnServiceDateId = new FeedScopedId(FEED_ID, TRIP_ON_SERVICE_DATE_ID);
    var tripRef = TripReference.builder().withTripOnServiceDateId(tripOnServiceDateId).build();
    var update = new ParsedDeleteTrip(tripRef, env.defaultServiceDate(), null, null);

    assertEquals(RealTimeState.SCHEDULED, env.tripData(TRIP_ID).realTimeState());

    var result = applier.apply(update);

    assertNotNull(result);
    var expectedTripId = new FeedScopedId(FEED_ID, TRIP_ID);
    assertEquals(expectedTripId, result.updatedTripTimes().getTrip().getId());
    assertEquals(RealTimeState.DELETED, result.updatedTripTimes().getRealTimeState());
  }

  @Test
  void testDeleteTrip_appliedToSnapshot() {
    var tripId = new FeedScopedId(FEED_ID, TRIP_ID);
    var tripRef = TripReference.ofTripId(tripId);
    var update = new ParsedDeleteTrip(tripRef, env.defaultServiceDate(), null, null);

    assertEquals(RealTimeState.SCHEDULED, env.tripData(TRIP_ID).realTimeState());

    var result = applier.apply(update);

    assertNotNull(result);
    snapshotManager.updateBuffer(result.realTimeTripUpdate());

    snapshotManager.purgeAndCommit();

    var tripData = env.tripData(TRIP_ID);
    assertEquals(RealTimeState.DELETED, tripData.realTimeState());
  }

  @Test
  void testCancelTrip_byTripId_success() {
    var tripId = new FeedScopedId(FEED_ID, TRIP_ID);
    var tripRef = TripReference.ofTripId(tripId);
    var update = new ParsedCancelTrip(tripRef, env.defaultServiceDate(), null, null);

    assertEquals(RealTimeState.SCHEDULED, env.tripData(TRIP_ID).realTimeState());

    var result = applier.apply(update);

    assertNotNull(result);
    assertEquals(tripId, result.updatedTripTimes().getTrip().getId());
    assertEquals(RealTimeState.CANCELED, result.updatedTripTimes().getRealTimeState());
  }

  @Test
  void testCancelTrip_byTripOnServiceDateId_success() {
    var tripOnServiceDateId = new FeedScopedId(FEED_ID, TRIP_ON_SERVICE_DATE_ID);
    var tripRef = TripReference.builder().withTripOnServiceDateId(tripOnServiceDateId).build();
    var update = new ParsedCancelTrip(tripRef, env.defaultServiceDate(), null, null);

    assertEquals(RealTimeState.SCHEDULED, env.tripData(TRIP_ID).realTimeState());

    var result = applier.apply(update);

    assertNotNull(result);
    var expectedTripId = new FeedScopedId(FEED_ID, TRIP_ID);
    assertEquals(expectedTripId, result.updatedTripTimes().getTrip().getId());
    assertEquals(RealTimeState.CANCELED, result.updatedTripTimes().getRealTimeState());
  }

  @Test
  void testCancelTrip_appliedToSnapshot() {
    var tripId = new FeedScopedId(FEED_ID, TRIP_ID);
    var tripRef = TripReference.ofTripId(tripId);
    var update = new ParsedCancelTrip(tripRef, env.defaultServiceDate(), null, null);

    assertEquals(RealTimeState.SCHEDULED, env.tripData(TRIP_ID).realTimeState());

    var result = applier.apply(update);

    assertNotNull(result);
    snapshotManager.updateBuffer(result.realTimeTripUpdate());

    snapshotManager.purgeAndCommit();

    var tripData = env.tripData(TRIP_ID);
    assertEquals(RealTimeState.CANCELED, tripData.realTimeState());
  }
}
