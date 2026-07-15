package org.opentripplanner.updater.trip;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.opentripplanner.updater.spi.UpdateErrorType;
import org.opentripplanner.updater.spi.UpdateException;
import org.opentripplanner.updater.trip.model.TripReference;
import org.opentripplanner.updater.trip.model.TripRevision;

class NoOpFuzzyTripMatcherTest {

  @Test
  void match_alwaysThrowsException() {
    var matcher = NoOpFuzzyTripMatcher.INSTANCE;
    var tripReference = TripReference.builder().build();
    var parsedUpdate = TripRevision.builder(tripReference, LocalDate.of(2024, 1, 15)).build();

    var exception = assertThrows(UpdateException.class, () ->
      matcher.match(tripReference, parsedUpdate, LocalDate.of(2024, 1, 15))
    );
    assertEquals(UpdateErrorType.NO_FUZZY_TRIP_MATCH, exception.errorType());
  }

  @Test
  void singleton_isSameInstance() {
    var instance1 = NoOpFuzzyTripMatcher.INSTANCE;
    var instance2 = NoOpFuzzyTripMatcher.INSTANCE;
    assertTrue(instance1 == instance2);
  }
}
