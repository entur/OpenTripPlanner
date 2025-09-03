package org.opentripplanner.ext.empiricaldelay.internal.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.opentripplanner.ext.empiricaldelay.model.EmpiricalDelay;

class EmpiricalDelayByServiceIdTest {

  private static final String WEEKEND = "WEEKEND";
  private static final EmpiricalDelay DELAY_STOP_A = new EmpiricalDelay(25, 50);
  private static final EmpiricalDelay DELAY_STOP_B = new EmpiricalDelay(7, 45);
  private static final int STOP_POS_A = 0;
  private static final int STOP_POS_B = 1;
  private final EmpiricalDelayByServiceId subject = new EmpiricalDelayByServiceId();

  @Test
  void putAndGet() {
    subject.put(WEEKEND, List.of(DELAY_STOP_A, DELAY_STOP_B));

    assertEquals(DELAY_STOP_A, subject.get(WEEKEND, STOP_POS_A).get());
    assertEquals(DELAY_STOP_B, subject.get(WEEKEND, STOP_POS_B).get());
  }

  /**
   * A missing service-id may happen if there is no empirical delay data on a given
   * day of week, but there exist data for another day. Note! There is typical a
   * delay calendar-service for each day-of-week.
   */
  @Test
  void missingServiceIdShouldReturnEmpty() {
    subject.put(WEEKEND, List.of(DELAY_STOP_A, DELAY_STOP_B));
    assertEquals(Optional.empty(), subject.get("MONDAY", STOP_POS_A));
  }

  @Test
  void testStopPosOutOfBounds() {
    subject.put(WEEKEND, List.of(DELAY_STOP_A, DELAY_STOP_B));

    assertThrows(IndexOutOfBoundsException.class, () -> subject.get(WEEKEND, -1));
    assertThrows(IndexOutOfBoundsException.class, () -> subject.get(WEEKEND, 2));
  }
}
