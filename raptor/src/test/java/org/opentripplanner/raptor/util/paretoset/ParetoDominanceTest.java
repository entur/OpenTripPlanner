package org.opentripplanner.raptor.util.paretoset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.opentripplanner.raptor.util.paretoset.ParetoDominance.BOTH;
import static org.opentripplanner.raptor.util.paretoset.ParetoDominance.LEFT;
import static org.opentripplanner.raptor.util.paretoset.ParetoDominance.NONE;
import static org.opentripplanner.raptor.util.paretoset.ParetoDominance.RIGHT;

import org.junit.jupiter.api.Test;

class ParetoDominanceTest {

  @Test
  void testOf() {
    assertEquals(BOTH, ParetoDominance.of("both"));
    assertEquals(LEFT, ParetoDominance.of("left"));
    assertEquals(RIGHT, ParetoDominance.of("right"));
    assertEquals(NONE, ParetoDominance.of("none"));
    assertEquals(LEFT, ParetoDominance.of("≺"));
    assertEquals(RIGHT, ParetoDominance.of("≻"));
    assertEquals(NONE, ParetoDominance.of("≡"));
    assertEquals(BOTH, ParetoDominance.of("∥"));

    // Mixed case
    assertEquals(BOTH, ParetoDominance.of("BOTH"));
    assertEquals(BOTH, ParetoDominance.of("bOtH"));
  }

  @Test
  void testOfLeftRight() {
    assertEquals(LEFT, ParetoDominance.of(true, false));
    assertEquals(RIGHT, ParetoDominance.of(false, true));
    assertEquals(BOTH, ParetoDominance.of(true, true));
    assertEquals(NONE, ParetoDominance.of(false, false));
  }

  @Test
  void testToString() {
    assertEquals("≺", LEFT.toString());
    assertEquals("≻", RIGHT.toString());
    assertEquals("≡", NONE.toString());
    assertEquals("∥", BOTH.toString());
  }
}
