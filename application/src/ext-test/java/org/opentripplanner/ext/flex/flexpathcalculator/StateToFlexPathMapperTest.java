package org.opentripplanner.ext.flex.flexpathcalculator;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.opentripplanner.street.search.state.TestStateBuilder.ofDriving;

import org.junit.jupiter.api.Test;

class StateToFlexPathMapperTest {

  private static final int EPSILON = 100;

  @Test
  void departAt() {
    var state = ofDriving().streetEdge().streetEdge().streetEdge().build();
    var flexPath = StateToFlexPathMapper.map(state);
    assertThat(flexPath.distanceMeters).isWithin(EPSILON).of(471509);
    assertEquals(42100, flexPath.durationSeconds);
    assertEquals("LINESTRING (1 1, 2 2, 3 3, 4 4)", flexPath.getGeometry().toString());
  }

  @Test
  void arriveBy() {
    var state = ofDriving().streetEdge().streetEdge().streetEdge().build().reverse();
    var flexPath = StateToFlexPathMapper.map(state);
    assertThat(flexPath.distanceMeters).isWithin(EPSILON).of(471509);
    assertEquals(42100, flexPath.durationSeconds);
    assertEquals("LINESTRING (1 1, 2 2, 3 3, 4 4)", flexPath.getGeometry().toString());
  }
}
