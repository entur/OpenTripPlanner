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
    .withTimeLimit(Duration.ofHours(1))
    .build();

  @Test
  void withinLimit() {
    assertFalse(RULE.withinTimeLimit(Duration.ofMinutes(61)));
    assertTrue(RULE.withinTimeLimit(Duration.ofMinutes(59)));
    assertTrue(RULE.withinTimeLimit(Duration.ofMinutes(60)));
  }

  @Test
  void negativeDuration() {
    assertThrows(IllegalArgumentException.class, () ->
      RULE.withinTimeLimit(Duration.ofMinutes(-1))
    );
  }
}
