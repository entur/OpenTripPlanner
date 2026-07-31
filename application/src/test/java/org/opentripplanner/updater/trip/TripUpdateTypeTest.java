package org.opentripplanner.updater.trip;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TripUpdateTypeTest {

  @Test
  void allValuesExist() {
    assertEquals(6, TripUpdateType.values().length);
  }
}
