package org.opentripplanner.ext.updater.trip.unified;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TripUpdateTypeTest {

  @Test
  void allValuesExist() {
    assertEquals(6, TripUpdateType.values().length);
  }
}
