package org.opentripplanner.ext.empiricaldelay.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.Serializable;
import org.junit.jupiter.api.Test;

class EmpiricalDelayTest {

  private static final int MIN_PERCENTILE = 10;
  private static final int MAX_PERCENTILE = 50;

  private final EmpiricalDelay subject = new EmpiricalDelay(MIN_PERCENTILE, MAX_PERCENTILE);

  @Test
  void minPercentil() {
    assertEquals(MIN_PERCENTILE, subject.minPercentile());
  }

  @Test
  void maxPercentil() {
    assertEquals(MAX_PERCENTILE, subject.maxPercentile());
  }

  @Test
  void testToString() {
    assertEquals("[10s, 50s]", subject.toString());
  }

  @Test
  void isSerializable() {
    assertTrue(subject instanceof Serializable);
  }
}
