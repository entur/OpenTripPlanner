package org.opentripplanner.ext.fares.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opentripplanner.transit.model._data.TimetableRepositoryForTest.id;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class FareTransferRuleTest {

  private static final FareTransferRule RULE = FareTransferRule.of()
    .withId(id("1"))
    .withTimeLimit(TimeLimitType.DEPARTURE_TO_ARRIVAL, Duration.ofHours(1))
    .build();

  @Test
  void withinLimit() {
    assertFalse(RULE.belowTimeLimit(Duration.ofMinutes(61)));
    assertTrue(RULE.belowTimeLimit(Duration.ofMinutes(59)));
    assertTrue(RULE.belowTimeLimit(Duration.ofMinutes(60)));
  }

  @Test
  void negativeDuration() {
    assertThrows(IllegalArgumentException.class, () -> RULE.belowTimeLimit(Duration.ofMinutes(-1)));
  }
}
