package org.opentripplanner.osm.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class OsmNodeTest {

  public static Stream<Arguments> entranceTestCases() {
    var mainEntrance = new OsmNode();
    mainEntrance.addTag("entrance", "main");

    var trainStationEntrance = new OsmNode();
    trainStationEntrance.addTag("railway", "train_station_entrance");

    var subwayEntrance = new OsmNode();
    subwayEntrance.addTag("railway", "subway_entrance");

    var publicTransportEntrance = new OsmNode();
    publicTransportEntrance.addTag("public_transport", "entrance");

    var trainStationEntranceWithEntranceTag = new OsmNode();
    trainStationEntranceWithEntranceTag.addTag("railway", "train_station_entrance");
    trainStationEntranceWithEntranceTag.addTag("entrance", "main");

    var notAnEntrance = new OsmNode();
    notAnEntrance.addTag("entrance", "no");

    var emergencyEntrance = new OsmNode();
    notAnEntrance.addTag("entrance", "emergency");

    return Stream.of(
      Arguments.argumentSet("main entrance", mainEntrance, true, false),
      Arguments.argumentSet("train station entrance", trainStationEntrance, true, true),
      Arguments.argumentSet("subway entrance", subwayEntrance, true, true),
      Arguments.argumentSet("public transport entrance", publicTransportEntrance, true, true),
      Arguments.argumentSet(
        "train station entrance with entrance tag",
        trainStationEntranceWithEntranceTag,
        true,
        true
      ),
      Arguments.argumentSet("not an entrance", notAnEntrance, false, false),
      Arguments.argumentSet("emergency entrance", emergencyEntrance, false, false)
    );
  }

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

  @ParameterizedTest
  @MethodSource("entranceTestCases")
  public void isEntrance(OsmNode node, boolean entrance, boolean stationEntrance) {
    assertEquals(entrance, node.isEntrance());
    assertEquals(stationEntrance, node.isStationEntrance());
  }
}
