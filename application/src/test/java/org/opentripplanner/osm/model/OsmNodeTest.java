package org.opentripplanner.osm.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class OsmNodeTest {

  @Test
  void lowerCaseKeys() {
    var entity = OsmNode.of().setTag("foo", "bar").setTag("FOO", "baz").build();
    assertEquals("baz", entity.getTag("foo"));
  }

  @Test
  public void isBarrier() {
    OsmNode node = OsmNode.of().build();
    assertFalse(node.isBarrier());

    node = OsmNode.of().setTag("barrier", "unknown").build();
    assertFalse(node.isBarrier());

    node = OsmNode.of().setTag("barrier", "bollard").build();
    assertTrue(node.isBarrier());

    node = OsmNode.of().setTag("access", "no").build();
    assertTrue(node.isBarrier());
  }

  @Test
  public void isTaggedBarrierCrossing() {
    OsmNode node = OsmNode.of().build();
    assertFalse(node.isTaggedBarrierCrossing());

    node = OsmNode.of().setTag("barrier", "gate").build();
    assertTrue(node.isTaggedBarrierCrossing());

    node = OsmNode.of().setTag("motor_vehicle", "yes").build();
    assertTrue(node.isTaggedBarrierCrossing());

    node = OsmNode.of().setTag("motor_vehicle", "no").build();
    assertTrue(node.isTaggedBarrierCrossing());

    node = OsmNode.of().setTag("access", "yes").build();
    assertTrue(node.isTaggedBarrierCrossing());

    node = OsmNode.of().setTag("access", "no").build();
    assertTrue(node.isTaggedBarrierCrossing());

    node = OsmNode.of().setTag("entrance", "main").build();
    assertTrue(node.isTaggedBarrierCrossing());
  }
}
