package org.opentripplanner.updater.trip;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.transit.model._data.TransitTestEnvironment;
import org.opentripplanner.transit.model.framework.Result;
import org.opentripplanner.transit.model.timetable.RealTimeTripUpdate;
import org.opentripplanner.transit.service.TransitService;
import org.opentripplanner.updater.spi.UpdateError;
import org.opentripplanner.updater.trip.model.ParsedTripUpdate;
import org.opentripplanner.updater.trip.model.TripReference;
import org.opentripplanner.updater.trip.model.TripUpdateType;

class TripUpdateApplierTest {

  private static final LocalDate SERVICE_DATE = LocalDate.of(2024, 1, 15);

  private String feedId;
  private TransitService transitService;
  private TripResolver tripResolver;
  private StopResolver stopResolver;

  @BeforeEach
  void setUp() {
    var env = TransitTestEnvironment.of().build();
    feedId = env.feedId();
    transitService = env.transitService();
    tripResolver = new TripResolver(transitService);
    stopResolver = new StopResolver(transitService);
  }

  @Test
  void applierContextHasRequiredFields() {
    var context = new TripUpdateApplierContext(feedId, null, tripResolver, stopResolver);

    assertEquals(feedId, context.feedId());
    assertNull(context.snapshotManager());
    assertNotNull(context.tripResolver());
    assertNotNull(context.stopResolver());
  }

  @Test
  void mockApplierReturnsSuccess() {
    var tripId = new FeedScopedId(feedId, "trip1");
    var tripRef = TripReference.ofTripId(tripId);
    var parsedUpdate = ParsedTripUpdate.builder(
      TripUpdateType.UPDATE_EXISTING,
      tripRef,
      SERVICE_DATE
    ).build();

    var applier = new MockTripUpdateApplier(true);
    var context = new TripUpdateApplierContext(feedId, null, tripResolver, stopResolver);

    var result = applier.apply(parsedUpdate, context);

    assertTrue(result.isSuccess());
  }

  @Test
  void mockApplierReturnsFailure() {
    var tripId = new FeedScopedId(feedId, "trip1");
    var tripRef = TripReference.ofTripId(tripId);
    var parsedUpdate = ParsedTripUpdate.builder(
      TripUpdateType.UPDATE_EXISTING,
      tripRef,
      SERVICE_DATE
    ).build();

    var applier = new MockTripUpdateApplier(false);
    var context = new TripUpdateApplierContext(feedId, null, tripResolver, stopResolver);

    var result = applier.apply(parsedUpdate, context);

    assertFalse(result.isSuccess());
    assertEquals(UpdateError.UpdateErrorType.TRIP_NOT_FOUND, result.failureValue().errorType());
  }

  /**
   * Mock implementation for testing the applier interface contract.
   */
  static class MockTripUpdateApplier implements TripUpdateApplier {

    private final boolean returnSuccess;

    MockTripUpdateApplier(boolean returnSuccess) {
      this.returnSuccess = returnSuccess;
    }

    @Override
    public Result<RealTimeTripUpdate, UpdateError> apply(
      ParsedTripUpdate parsedUpdate,
      TripUpdateApplierContext context
    ) {
      if (returnSuccess) {
        return Result.success(null);
      } else {
        return Result.failure(
          new UpdateError(
            parsedUpdate.tripReference().tripId(),
            UpdateError.UpdateErrorType.TRIP_NOT_FOUND
          )
        );
      }
    }
  }
}
