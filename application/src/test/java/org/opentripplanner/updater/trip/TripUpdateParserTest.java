package org.opentripplanner.updater.trip;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.transit.model.framework.Result;
import org.opentripplanner.updater.spi.UpdateError;
import org.opentripplanner.updater.trip.model.ParsedTripUpdate;
import org.opentripplanner.updater.trip.model.TripReference;
import org.opentripplanner.updater.trip.model.TripUpdateType;

class TripUpdateParserTest {

  private static final String FEED_ID = "F";

  @Test
  void mockParserReturnsSuccess() {
    var tripId = new FeedScopedId(FEED_ID, "trip1");
    var tripRef = TripReference.ofTripId(tripId);
    var serviceDate = LocalDate.of(2024, 1, 15);

    var parser = new MockTripUpdateParser(
      Result.success(
        ParsedTripUpdate.builder(TripUpdateType.UPDATE_EXISTING, tripRef, serviceDate).build()
      )
    );

    var result = parser.parse("test-input");

    assertTrue(result.isSuccess());
    assertEquals(TripUpdateType.UPDATE_EXISTING, result.successValue().updateType());
    assertEquals(tripRef, result.successValue().tripReference());
  }

  @Test
  void mockParserReturnsFailure() {
    var tripId = new FeedScopedId(FEED_ID, "trip1");

    var parser = new MockTripUpdateParser(
      Result.failure(new UpdateError(tripId, UpdateError.UpdateErrorType.TRIP_NOT_FOUND))
    );

    var result = parser.parse("test-input");

    assertFalse(result.isSuccess());
    assertEquals(UpdateError.UpdateErrorType.TRIP_NOT_FOUND, result.failureValue().errorType());
  }

  /**
   * Mock implementation for testing the parser interface contract.
   */
  static class MockTripUpdateParser implements TripUpdateParser<String> {

    private final Result<ParsedTripUpdate, UpdateError> result;

    MockTripUpdateParser(Result<ParsedTripUpdate, UpdateError> result) {
      this.result = result;
    }

    @Override
    public Result<ParsedTripUpdate, UpdateError> parse(String update) {
      return result;
    }
  }
}
