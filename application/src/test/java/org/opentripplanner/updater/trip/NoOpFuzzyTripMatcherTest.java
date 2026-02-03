package org.opentripplanner.updater.trip;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.opentripplanner.updater.spi.UpdateError;
import org.opentripplanner.updater.trip.model.ParsedTripUpdate;
import org.opentripplanner.updater.trip.model.TripReference;
import org.opentripplanner.updater.trip.model.TripUpdateOptions;
import org.opentripplanner.updater.trip.model.TripUpdateType;

class NoOpFuzzyTripMatcherTest {

  @Test
  void match_alwaysReturnsFailure() {
    var matcher = NoOpFuzzyTripMatcher.INSTANCE;
    var tripReference = TripReference.builder().build();
    var parsedUpdate = ParsedTripUpdate.builder(
      TripUpdateType.UPDATE_EXISTING,
      tripReference,
      LocalDate.of(2024, 1, 15)
    )
      .withOptions(TripUpdateOptions.siriDefaults())
      .build();

    var result = matcher.match(tripReference, parsedUpdate, LocalDate.of(2024, 1, 15));

    assertTrue(result.isFailure());
    assertEquals(
      UpdateError.UpdateErrorType.NO_FUZZY_TRIP_MATCH,
      result.failureValue().errorType()
    );
  }

  @Test
  void singleton_isSameInstance() {
    var instance1 = NoOpFuzzyTripMatcher.INSTANCE;
    var instance2 = NoOpFuzzyTripMatcher.INSTANCE;
    assertTrue(instance1 == instance2);
  }
}
