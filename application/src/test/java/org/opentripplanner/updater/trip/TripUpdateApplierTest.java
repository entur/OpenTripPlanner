package org.opentripplanner.updater.trip;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.transit.model._data.TransitTestEnvironment;
import org.opentripplanner.transit.model.framework.Result;
import org.opentripplanner.updater.spi.UpdateError;
import org.opentripplanner.updater.trip.handlers.TripUpdateResult;
import org.opentripplanner.updater.trip.model.ParsedTripUpdate;
import org.opentripplanner.updater.trip.model.TripReference;
import org.opentripplanner.updater.trip.model.TripUpdateType;

class TripUpdateApplierTest {

  private static final LocalDate SERVICE_DATE = LocalDate.of(2024, 1, 15);

  @Test
  void mockApplierReturnsSuccess() {
    var env = TransitTestEnvironment.of().build();
    var tripId = new FeedScopedId(env.feedId(), "trip1");
    var tripRef = TripReference.ofTripId(tripId);
    var parsedUpdate = ParsedTripUpdate.builder(
      TripUpdateType.UPDATE_EXISTING,
      tripRef,
      SERVICE_DATE
    ).build();

    var applier = new MockTripUpdateApplier(true);

    var result = applier.apply(parsedUpdate);

    assertTrue(result.isSuccess());
  }

  @Test
  void mockApplierReturnsFailure() {
    var env = TransitTestEnvironment.of().build();
    var tripId = new FeedScopedId(env.feedId(), "trip1");
    var tripRef = TripReference.ofTripId(tripId);
    var parsedUpdate = ParsedTripUpdate.builder(
      TripUpdateType.UPDATE_EXISTING,
      tripRef,
      SERVICE_DATE
    ).build();

    var applier = new MockTripUpdateApplier(false);

    var result = applier.apply(parsedUpdate);

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
    public Result<TripUpdateResult, UpdateError> apply(ParsedTripUpdate parsedUpdate) {
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
