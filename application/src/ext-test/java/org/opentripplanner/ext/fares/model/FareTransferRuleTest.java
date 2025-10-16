package org.opentripplanner.ext.fares.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opentripplanner.transit.model._data.TimetableRepositoryForTest.id;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class FareTransferRuleTest {

  @Test
  void withinLimit() {
    var rule = FareTransferRule.of().withId(id("1")).withTimeLimit(Duration.ofHours(1)).build();
    assertFalse(rule.withinTimeLimit(Duration.ofMinutes(61)));
    assertTrue(rule.withinTimeLimit(Duration.ofMinutes(59)));
    assertTrue(rule.withinTimeLimit(Duration.ofMinutes(60)));
  }
}
