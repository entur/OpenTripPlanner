package org.opentripplanner.updater.trip;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.transit.model.framework.Result;
import org.opentripplanner.updater.spi.UpdateError;
import org.opentripplanner.updater.trip.model.ParsedTripUpdate;
import org.opentripplanner.updater.trip.model.TripReference;
import org.opentripplanner.updater.trip.model.TripUpdateType;

class TripUpdateParserTest {

  private static final String FEED_ID = "F";
  private static final ZoneId TIME_ZONE = ZoneId.of("Europe/Oslo");

  @Test
  void parserContextHasRequiredFields() {
    var context = new TripUpdateParserContext(FEED_ID, TIME_ZONE, () -> LocalDate.of(2024, 1, 15));

    assertEquals(FEED_ID, context.feedId());
    assertEquals(TIME_ZONE, context.timeZone());
    assertEquals(LocalDate.of(2024, 1, 15), context.localDateNow().get());
  }

  @Test
  void parserContextCreatesFeedScopedId() {
    var context = new TripUpdateParserContext(FEED_ID, TIME_ZONE, () -> LocalDate.now());

    var id = context.createId("trip1");

    assertEquals(FEED_ID, id.getFeedId());
    assertEquals("trip1", id.getId());
  }

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

    var context = new TripUpdateParserContext(FEED_ID, TIME_ZONE, () -> serviceDate);
    var result = parser.parse("test-input", context);

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

    var context = new TripUpdateParserContext(FEED_ID, TIME_ZONE, () -> LocalDate.now());
    var result = parser.parse("test-input", context);

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
    public Result<ParsedTripUpdate, UpdateError> parse(
      String update,
      TripUpdateParserContext context
    ) {
      return result;
    }
  }
}
