package org.opentripplanner.osm.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class OsmNodeTest {

  @Test
  public void isBarrier() {
    OsmNode node = OsmNode.of().build();
    assertFalse(node.isBarrier());

    node = OsmNode.of().addTag("barrier", "unknown").build();
    assertFalse(node.isBarrier());

    node = OsmNode.of().addTag("barrier", "bollard").build();
    assertTrue(node.isBarrier());

    node = OsmNode.of().addTag("access", "no").build();
    assertTrue(node.isBarrier());
  }

  @Test
  public void isTaggedBarrierCrossing() {
    OsmNode node = OsmNode.of().build();
    assertFalse(node.isTaggedBarrierCrossing());

    node = OsmNode.of().addTag("barrier", "gate").build();
    assertTrue(node.isTaggedBarrierCrossing());

    node = OsmNode.of().addTag("motor_vehicle", "yes").build();
    assertTrue(node.isTaggedBarrierCrossing());

    node = OsmNode.of().addTag("motor_vehicle", "no").build();
    assertTrue(node.isTaggedBarrierCrossing());

    node = OsmNode.of().addTag("access", "yes").build();
    assertTrue(node.isTaggedBarrierCrossing());

    node = OsmNode.of().addTag("access", "no").build();
    assertTrue(node.isTaggedBarrierCrossing());

    node = OsmNode.of().addTag("entrance", "main").build();
    assertTrue(node.isTaggedBarrierCrossing());
  }
}
