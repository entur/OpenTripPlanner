package org.opentripplanner.updater.trip;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.updater.spi.UpdateErrorType;
import org.opentripplanner.updater.spi.UpdateException;
import org.opentripplanner.updater.trip.model.ParsedTripUpdate;
import org.opentripplanner.updater.trip.model.ScheduledTripUpdate;
import org.opentripplanner.updater.trip.model.TripReference;

class TripUpdateParserTest {

  private static final String FEED_ID = "F";

  @Test
  void mockParserReturnsSuccess() {
    var tripId = new FeedScopedId(FEED_ID, "trip1");
    var tripRef = TripReference.ofTripId(tripId);
    var serviceDate = LocalDate.of(2024, 1, 15);

    var expectedResult = ScheduledTripUpdate.builder(tripRef, serviceDate).build();
    var parser = new MockTripUpdateParser(expectedResult, null);

    var result = parser.parse("test-input");

    assertInstanceOf(ScheduledTripUpdate.class, result);
    assertEquals(tripRef, result.tripReference());
  }

  @Test
  void mockParserReturnsFailure() {
    var tripId = new FeedScopedId(FEED_ID, "trip1");

    var parser = new MockTripUpdateParser(
      null,
      UpdateException.of(tripId, UpdateErrorType.TRIP_NOT_FOUND)
    );

    var ex = assertThrows(UpdateException.class, () -> parser.parse("test-input"));
    assertEquals(UpdateErrorType.TRIP_NOT_FOUND, ex.errorType());
  }

  /**
   * Mock implementation for testing the parser interface contract.
   */
  static class MockTripUpdateParser implements TripUpdateParser<String> {

    private final ParsedTripUpdate result;
    private final UpdateException exception;

    MockTripUpdateParser(ParsedTripUpdate result, UpdateException exception) {
      this.result = result;
      this.exception = exception;
    }

    @Override
    public ParsedTripUpdate parse(String update) throws UpdateException {
      if (exception != null) {
        throw exception;
      }
      return result;
    }
  }
}
