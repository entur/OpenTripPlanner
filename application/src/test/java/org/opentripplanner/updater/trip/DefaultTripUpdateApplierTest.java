package org.opentripplanner.updater.trip;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.transit.model._data.TransitTestEnvironment;
import org.opentripplanner.transit.model._data.TripInput;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.timetable.RealTimeState;
import org.opentripplanner.transit.model.timetable.Trip;
import org.opentripplanner.transit.service.DefaultTransitService;
import org.opentripplanner.transit.service.TransitEditorService;
import org.opentripplanner.updater.spi.UpdateError;
import org.opentripplanner.updater.trip.model.ParsedTripUpdate;
import org.opentripplanner.updater.trip.model.TripReference;
import org.opentripplanner.updater.trip.model.TripUpdateType;

/**
 * Tests for DefaultTripUpdateApplier.
 */
class DefaultTripUpdateApplierTest {

  private static final LocalDate SERVICE_DATE = LocalDate.of(2024, 5, 30);
  private static final String TRIP_ID = "trip1";

  private DefaultTripUpdateApplier applier;
  private TransitEditorService transitService;
  private TripUpdateApplierContext context;
  private TimetableSnapshotManager snapshotManager;

  private Trip testTrip;
  private TripPattern testPattern;
  private FeedScopedId tripId;
  private String FEED_ID;

  @BeforeEach
  void setUp() {
    // Create test environment builder
    var envBuilder = TransitTestEnvironment.of(SERVICE_DATE);

    // Create stops
    var stop1 = envBuilder.stop("stop1");
    var stop2 = envBuilder.stop("stop2");

    // Create route
    var route = envBuilder.route("route1");

    // Create trip input with stops
    var tripInput = TripInput.of(TRIP_ID)
      .withRoute(route)
      .addStop(stop1, "10:00:00", "10:00:00")
      .addStop(stop2, "10:10:00", "10:10:00");

    // Build the environment with the trip
    var env = envBuilder.addTrip(tripInput).build();

    // Get the trip and pattern from the environment
    var tripData = env.tripData(TRIP_ID);
    this.testTrip = tripData.trip();
    this.tripId = this.testTrip.getId();
    this.testPattern = tripData.tripPattern();
    this.FEED_ID = env.feedId();

    // Create transit service
    this.transitService = new DefaultTransitService(env.timetableRepository());

    // Use the snapshot manager from the environment
    this.snapshotManager = env.timetableSnapshotManager();

    // Create applier and context
    this.applier = new DefaultTripUpdateApplier(transitService);
    this.context = new TripUpdateApplierContext(FEED_ID, snapshotManager);
  }

  @Test
  void testCancelTrip_success() {
    var update = ParsedTripUpdate.builder(
      TripUpdateType.CANCEL_TRIP,
      TripReference.builder().withTripId(tripId).build(),
      SERVICE_DATE
    ).build();

    var result = applier.apply(update, context);

    assertTrue(result.isSuccess());
    var realTimeUpdate = result.successValue();
    assertEquals(testPattern, realTimeUpdate.pattern());
    assertEquals(SERVICE_DATE, realTimeUpdate.serviceDate());
    assertEquals(RealTimeState.CANCELED, realTimeUpdate.updatedTripTimes().getRealTimeState());
  }

  @Test
  void testDeleteTrip_success() {
    var update = ParsedTripUpdate.builder(
      TripUpdateType.DELETE_TRIP,
      TripReference.builder().withTripId(tripId).build(),
      SERVICE_DATE
    ).build();

    var result = applier.apply(update, context);

    assertTrue(result.isSuccess());
    var realTimeUpdate = result.successValue();
    assertEquals(testPattern, realTimeUpdate.pattern());
    assertEquals(SERVICE_DATE, realTimeUpdate.serviceDate());
    assertEquals(RealTimeState.DELETED, realTimeUpdate.updatedTripTimes().getRealTimeState());
  }

  @Test
  void testCancelTrip_tripNotFound() {
    var nonExistentTripId = new FeedScopedId(FEED_ID, "non-existent-trip");
    var update = ParsedTripUpdate.builder(
      TripUpdateType.CANCEL_TRIP,
      TripReference.builder().withTripId(nonExistentTripId).build(),
      SERVICE_DATE
    ).build();

    var result = applier.apply(update, context);

    assertTrue(result.isFailure());
    assertEquals(UpdateError.UpdateErrorType.TRIP_NOT_FOUND, result.failureValue().errorType());
    assertEquals(nonExistentTripId, result.failureValue().tripId());
  }

  @Test
  void testUpdateExisting_success() {
    var update = ParsedTripUpdate.builder(
      TripUpdateType.UPDATE_EXISTING,
      TripReference.builder().withTripId(tripId).build(),
      SERVICE_DATE
    )
      .addStopTimeUpdate(
        org.opentripplanner.updater.trip.model.ParsedStopTimeUpdate.builder(
          org.opentripplanner.updater.trip.model.StopReference.ofStopId(
            new FeedScopedId(FEED_ID, "stop1")
          )
        )
          .withArrivalUpdate(org.opentripplanner.updater.trip.model.TimeUpdate.ofDelay(120))
          .withDepartureUpdate(org.opentripplanner.updater.trip.model.TimeUpdate.ofDelay(120))
          .build()
      )
      .addStopTimeUpdate(
        org.opentripplanner.updater.trip.model.ParsedStopTimeUpdate.builder(
          org.opentripplanner.updater.trip.model.StopReference.ofStopId(
            new FeedScopedId(FEED_ID, "stop2")
          )
        )
          .withArrivalUpdate(org.opentripplanner.updater.trip.model.TimeUpdate.ofDelay(180))
          .withDepartureUpdate(org.opentripplanner.updater.trip.model.TimeUpdate.ofDelay(180))
          .build()
      )
      .build();

    var result = applier.apply(update, context);

    assertTrue(result.isSuccess());
    var realTimeUpdate = result.successValue();
    assertEquals(testPattern, realTimeUpdate.pattern());
    assertEquals(SERVICE_DATE, realTimeUpdate.serviceDate());
    assertEquals(RealTimeState.MODIFIED, realTimeUpdate.updatedTripTimes().getRealTimeState());

    // Verify delays were applied
    var tripTimes = realTimeUpdate.updatedTripTimes();
    assertEquals(120, tripTimes.getArrivalDelay(0));
    assertEquals(120, tripTimes.getDepartureDelay(0));
    assertEquals(180, tripTimes.getArrivalDelay(1));
    assertEquals(180, tripTimes.getDepartureDelay(1));
  }

  @Test
  void testUpdateExisting_noUpdates() {
    var update = ParsedTripUpdate.builder(
      TripUpdateType.UPDATE_EXISTING,
      TripReference.builder().withTripId(tripId).build(),
      SERVICE_DATE
    ).build();

    var result = applier.apply(update, context);

    assertTrue(result.isFailure());
    assertEquals(UpdateError.UpdateErrorType.NO_UPDATES, result.failureValue().errorType());
  }

  @Test
  void testUpdateExisting_tripNotFound() {
    var nonExistentTripId = new FeedScopedId(FEED_ID, "non-existent-trip");
    var update = ParsedTripUpdate.builder(
      TripUpdateType.UPDATE_EXISTING,
      TripReference.builder().withTripId(nonExistentTripId).build(),
      SERVICE_DATE
    )
      .addStopTimeUpdate(
        org.opentripplanner.updater.trip.model.ParsedStopTimeUpdate.builder(
          org.opentripplanner.updater.trip.model.StopReference.ofStopId(
            new FeedScopedId(FEED_ID, "stop1")
          )
        )
          .withArrivalUpdate(org.opentripplanner.updater.trip.model.TimeUpdate.ofDelay(60))
          .build()
      )
      .build();

    var result = applier.apply(update, context);

    assertTrue(result.isFailure());
    assertEquals(UpdateError.UpdateErrorType.TRIP_NOT_FOUND, result.failureValue().errorType());
  }

  @Test
  void testAddNewTrip_success() {
    var newTripId = new FeedScopedId(FEED_ID, "new-trip-1");
    var routeId = new FeedScopedId(FEED_ID, "route1");
    var stopRef1 = org.opentripplanner.updater.trip.model.StopReference.ofStopId(
      new FeedScopedId(FEED_ID, "stop1")
    );
    var stopRef2 = org.opentripplanner.updater.trip.model.StopReference.ofStopId(
      new FeedScopedId(FEED_ID, "stop2")
    );

    var stopTimeUpdate1 = org.opentripplanner.updater.trip.model.ParsedStopTimeUpdate.builder(
      stopRef1
    )
      .withArrivalUpdate(org.opentripplanner.updater.trip.model.TimeUpdate.ofAbsolute(36000, null))
      .withDepartureUpdate(
        org.opentripplanner.updater.trip.model.TimeUpdate.ofAbsolute(36000, null)
      )
      .build();

    var stopTimeUpdate2 = org.opentripplanner.updater.trip.model.ParsedStopTimeUpdate.builder(
      stopRef2
    )
      .withArrivalUpdate(org.opentripplanner.updater.trip.model.TimeUpdate.ofAbsolute(36600, null))
      .withDepartureUpdate(
        org.opentripplanner.updater.trip.model.TimeUpdate.ofAbsolute(36600, null)
      )
      .build();

    var update = ParsedTripUpdate.builder(
      TripUpdateType.ADD_NEW_TRIP,
      TripReference.builder().withTripId(newTripId).withRouteId(routeId).build(),
      SERVICE_DATE
    )
      .addStopTimeUpdate(stopTimeUpdate1)
      .addStopTimeUpdate(stopTimeUpdate2)
      .build();

    var result = applier.apply(update, context);

    assertTrue(result.isSuccess());
    var tripUpdate = result.successValue();
    assertEquals(newTripId, tripUpdate.updatedTripTimes().getTrip().getId());
    assertEquals(RealTimeState.ADDED, tripUpdate.updatedTripTimes().getRealTimeState());
  }

  @Test
  void testAddNewTrip_noStopTimeUpdates() {
    var newTripId = new FeedScopedId(FEED_ID, "new-trip-2");
    var routeId = new FeedScopedId(FEED_ID, "route1");

    var update = ParsedTripUpdate.builder(
      TripUpdateType.ADD_NEW_TRIP,
      TripReference.builder().withTripId(newTripId).withRouteId(routeId).build(),
      SERVICE_DATE
    ).build();

    var result = applier.apply(update, context);

    assertTrue(result.isFailure());
    assertEquals(UpdateError.UpdateErrorType.NO_UPDATES, result.failureValue().errorType());
  }

  @Test
  void testAddNewTrip_routeNotFound() {
    var newTripId = new FeedScopedId(FEED_ID, "new-trip-3");
    var unknownRouteId = new FeedScopedId(FEED_ID, "unknown-route");
    var stopRef = org.opentripplanner.updater.trip.model.StopReference.ofStopId(
      new FeedScopedId(FEED_ID, "stop1")
    );

    var stopTimeUpdate = org.opentripplanner.updater.trip.model.ParsedStopTimeUpdate.builder(
      stopRef
    )
      .withArrivalUpdate(org.opentripplanner.updater.trip.model.TimeUpdate.ofAbsolute(36000, null))
      .withDepartureUpdate(
        org.opentripplanner.updater.trip.model.TimeUpdate.ofAbsolute(36000, null)
      )
      .build();

    var update = ParsedTripUpdate.builder(
      TripUpdateType.ADD_NEW_TRIP,
      TripReference.builder().withTripId(newTripId).withRouteId(unknownRouteId).build(),
      SERVICE_DATE
    )
      .addStopTimeUpdate(stopTimeUpdate)
      .build();

    var result = applier.apply(update, context);

    assertTrue(result.isFailure());
    assertEquals(UpdateError.UpdateErrorType.UNKNOWN, result.failureValue().errorType());
  }

  @Test
  void testModifyTrip_notImplemented() {
    var update = ParsedTripUpdate.builder(
      TripUpdateType.MODIFY_TRIP,
      TripReference.builder().withTripId(tripId).build(),
      SERVICE_DATE
    ).build();

    var result = applier.apply(update, context);

    assertTrue(result.isFailure());
    assertEquals(UpdateError.UpdateErrorType.UNKNOWN, result.failureValue().errorType());
  }

  @Test
  void testAddExtraCalls_notImplemented() {
    var update = ParsedTripUpdate.builder(
      TripUpdateType.ADD_EXTRA_CALLS,
      TripReference.builder().withTripId(tripId).build(),
      SERVICE_DATE
    ).build();

    var result = applier.apply(update, context);

    assertTrue(result.isFailure());
    assertEquals(UpdateError.UpdateErrorType.UNKNOWN, result.failureValue().errorType());
  }
}
