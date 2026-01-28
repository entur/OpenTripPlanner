package org.opentripplanner.updater.trip;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opentripplanner.transit.model._data.TransitTestEnvironment;
import org.opentripplanner.transit.service.TransitEditorService;
import org.opentripplanner.updater.spi.UpdateError;
import org.opentripplanner.updater.trip.model.ParsedTripUpdate;
import org.opentripplanner.updater.trip.model.TripReference;
import org.opentripplanner.updater.trip.model.TripUpdateType;

/**
 * Tests for DefaultTripUpdateApplier.
 */
class DefaultTripUpdateApplierTest {

  private DefaultTripUpdateApplier applier;
  private TransitEditorService transitService;
  private TripUpdateApplierContext context;

  @BeforeEach
  void setUp() {
    var env = TransitTestEnvironment.of().build();
    transitService = (TransitEditorService) env.transitService();
    applier = new DefaultTripUpdateApplier(transitService);
    var tripResolver = new TripResolver(env.transitService());
    var stopResolver = new StopResolver(env.transitService());
    var tripPatternCache = new org.opentripplanner.updater.trip.siri.SiriTripPatternCache(
      new org.opentripplanner.updater.trip.siri.SiriTripPatternIdGenerator(),
      env.transitService()::findPattern
    );
    context = new TripUpdateApplierContext(
      env.feedId(),
      null,
      tripResolver,
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
    assertEquals(UpdateError.UpdateErrorType.TRIP_NOT_FOUND, result.failureValue().errorType());
  }

  @Test
  void testDeleteTrip_notImplemented() {
    var update = ParsedTripUpdate.builder(
      TripUpdateType.DELETE_TRIP,
      TripReference.builder().build(),
      LocalDate.now()
    ).build();

    var result = applier.apply(update, context);

    assertTrue(result.isFailure());
    assertEquals(UpdateError.UpdateErrorType.UNKNOWN, result.failureValue().errorType());
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
  void testModifyTrip_notImplemented() {
    var update = ParsedTripUpdate.builder(
      TripUpdateType.MODIFY_TRIP,
      TripReference.builder().build(),
      LocalDate.now()
    ).build();

    var result = applier.apply(update, context);

    assertTrue(result.isFailure());
    assertEquals(UpdateError.UpdateErrorType.UNKNOWN, result.failureValue().errorType());
  }
}
