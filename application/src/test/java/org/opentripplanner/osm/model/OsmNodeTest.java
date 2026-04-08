package org.opentripplanner.osm.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class OsmNodeTest {

  @Test
  public void isBarrier() {
    OsmNode node = new OsmNode();
    assertFalse(node.isBarrier());

    node = new OsmNode();
    node.addTag("barrier", "unknown");
    assertFalse(node.isBarrier());

    node = new OsmNode();
    node.addTag("barrier", "bollard");
    assertTrue(node.isBarrier());

    node = new OsmNode();
    node.addTag("access", "no");
    assertTrue(node.isBarrier());
  }

  @Test
  public void isTaggedBarrierCrossing() {
    OsmNode node = new OsmNode();
    assertFalse(node.isTaggedBarrierCrossing());

    node = new OsmNode();
    node.addTag("barrier", "gate");
    assertTrue(node.isTaggedBarrierCrossing());

    node = new OsmNode();
    node.addTag("motor_vehicle", "yes");
    assertTrue(node.isTaggedBarrierCrossing());

    node = new OsmNode();
    node.addTag("motor_vehicle", "no");
    assertTrue(node.isTaggedBarrierCrossing());

    node = new OsmNode();
    node.addTag("access", "yes");
    assertTrue(node.isTaggedBarrierCrossing());

    node = new OsmNode();
    node.addTag("access", "no");
    assertTrue(node.isTaggedBarrierCrossing());

    node = new OsmNode();
    node.addTag("entrance", "main");
    assertTrue(node.isTaggedBarrierCrossing());
  }

  @Test
  public void isEntrance() {
    OsmNode node = new OsmNode();
    node.addTag("entrance", "main");
    assertTrue(node.isEntrance());
    assertFalse(node.isStationEntrance());
    node.addTag("railway", "train_station_entrance");
    assertTrue(node.isEntrance());

    node = new OsmNode();
    node.addTag("railway", "train_station_entrance");
    assertTrue(node.isEntrance());
    assertTrue(node.isStationEntrance());

    node = new OsmNode();
    node.addTag("railway", "subway_entrance");
    assertTrue(node.isEntrance());
    assertTrue(node.isStationEntrance());

    node = new OsmNode();
    node.addTag("public_transport", "entrance");
    assertTrue(node.isEntrance());
    assertTrue(node.isStationEntrance());
  }
}
