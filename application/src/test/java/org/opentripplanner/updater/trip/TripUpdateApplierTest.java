package org.opentripplanner.updater.trip;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.transit.model.TransitTestEnvironment;
import org.opentripplanner.updater.spi.UpdateErrorType;
import org.opentripplanner.updater.spi.UpdateException;
import org.opentripplanner.updater.trip.model.ParsedTripUpdate;
import org.opentripplanner.updater.trip.model.TripReference;
import org.opentripplanner.updater.trip.model.TripRevision;

class TripUpdateApplierTest {

  private static final LocalDate SERVICE_DATE = LocalDate.of(2024, 1, 15);

  @Test
  void mockApplierReturnsSuccess() {
    var env = TransitTestEnvironment.of().build();
    var tripId = new FeedScopedId(env.feedId(), "trip1");
    var tripRef = TripReference.ofTripId(tripId);
    var parsedUpdate = TripRevision.builder(tripRef, SERVICE_DATE).build();

    var applier = new MockTripUpdateApplier(true);

    assertDoesNotThrow(() -> applier.apply(parsedUpdate));
  }

  @Test
  void mockApplierReturnsFailure() {
    var env = TransitTestEnvironment.of().build();
    var tripId = new FeedScopedId(env.feedId(), "trip1");
    var tripRef = TripReference.ofTripId(tripId);
    var parsedUpdate = TripRevision.builder(tripRef, SERVICE_DATE).build();

    var applier = new MockTripUpdateApplier(false);

    var ex = assertThrows(UpdateException.class, () -> applier.apply(parsedUpdate));
    assertEquals(UpdateErrorType.TRIP_NOT_FOUND, ex.errorType());
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
    public TripUpdateResult apply(ParsedTripUpdate parsedUpdate) throws UpdateException {
      if (returnSuccess) {
        return null;
      } else {
        throw UpdateException.of(
          parsedUpdate.tripReference().tripId(),
          UpdateErrorType.TRIP_NOT_FOUND
        );
      }
    }
  }
}
