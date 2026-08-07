package org.opentripplanner.ext.updater.trip.unified.resolver;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.opentripplanner.ext.updater.trip.unified.model.command.ReviseTrip;
import org.opentripplanner.ext.updater.trip.unified.model.command.TripReference;

class NoOpFuzzyTripMatcherTest {

  @Test
  void match_neverHasAVerdict() {
    var matcher = NoOpFuzzyTripMatcher.INSTANCE;
    var tripReference = TripReference.builder().build();
    var command = ReviseTrip.builder(tripReference, LocalDate.of(2024, 1, 15)).build();

    assertThat(matcher.match(tripReference, command, LocalDate.of(2024, 1, 15))).isEmpty();
  }

  @Test
  void singleton_isSameInstance() {
    var instance1 = NoOpFuzzyTripMatcher.INSTANCE;
    var instance2 = NoOpFuzzyTripMatcher.INSTANCE;
    assertTrue(instance1 == instance2);
  }
}
