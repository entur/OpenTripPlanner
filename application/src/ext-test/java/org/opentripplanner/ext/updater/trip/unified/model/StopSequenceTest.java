package org.opentripplanner.ext.updater.trip.unified.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class StopSequenceTest {

  @Test
  void carriesTheFeedsNumber() {
    assertEquals(7, StopSequence.of(7).value());
    assertEquals(0, StopSequence.of(0).value());
  }

  @Test
  void rejectsANegativeNumber() {
    assertThrows(IllegalArgumentException.class, () -> StopSequence.of(-1));
    assertThrows(IllegalArgumentException.class, () -> StopSequence.of(Integer.MIN_VALUE));
  }

  @Test
  void equalsByValue() {
    assertEquals(StopSequence.of(7), StopSequence.of(7));
    assertEquals(StopSequence.of(7).hashCode(), StopSequence.of(7).hashCode());
    assertNotEquals(StopSequence.of(7), StopSequence.of(8));
  }

  @Test
  void printsTheNumber() {
    assertEquals("7", StopSequence.of(7).toString());
  }
}
