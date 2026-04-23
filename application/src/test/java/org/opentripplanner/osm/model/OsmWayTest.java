package org.opentripplanner.osm.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opentripplanner.osm.model.TraverseDirection.BACKWARD;
import static org.opentripplanner.osm.model.TraverseDirection.FORWARD;

import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.opentripplanner.osm.wayproperty.specifier.WayTestData;

class OsmWayTest {

  @Test
  void lowerCaseKeys() {
    var entity = OsmWay.of().setTag("foo", "bar").setTag("FOO", "baz").build();
    assertEquals("baz", entity.getTag("foo"));
  }

  @Test
  void testIsBicycleDismountForced() {
    OsmWay way = OsmWay.of().build();
    assertFalse(way.isBicycleDismountForced());

    way = OsmWay.of().setTag("bicycle", "dismount").build();
    assertTrue(way.isBicycleDismountForced());
  }

  @Test
  void testAreaMustContain3Nodes() {
    OsmWay way = OsmWay.of().setTag("area", "yes").build();
    assertFalse(way.isRoutableArea());
    way = way.copy().addNodeRef(1).build();
    assertFalse(way.isRoutableArea());
    way = way.copy().addNodeRef(2).build();
    assertFalse(way.isRoutableArea());
    way = way.copy().addNodeRef(3).build();
    assertTrue(way.isRoutableArea());
    way = way.copy().addNodeRef(4).build();
    assertTrue(way.isRoutableArea());
  }

  @Test
  void testAreaTags() {
    OsmWay platform = getClosedPolygon().copy().setTag("public_transport", "platform").build();
    assertTrue(platform.isRoutableArea());
    platform = platform.copy().setTag("area", "no").build();
    assertFalse(platform.isRoutableArea());

    OsmWay roundabout = getClosedPolygon().copy().setTag("highway", "roundabout").build();
    assertFalse(roundabout.isRoutableArea());

    OsmWay pedestrian = getClosedPolygon().copy().setTag("highway", "pedestrian").build();
    assertFalse(pedestrian.isRoutableArea());
    pedestrian = pedestrian.copy().setTag("area", "yes").build();
    assertTrue(pedestrian.isRoutableArea());

    OsmWay indoorArea = getClosedPolygon().copy().setTag("indoor", "area").build();
    assertTrue(indoorArea.isRoutableArea());

    OsmWay bikeParking = getClosedPolygon().copy().setTag("amenity", "bicycle_parking").build();
    assertTrue(bikeParking.isRoutableArea());

    OsmWay corridor = getClosedPolygon().copy().setTag("indoor", "corridor").build();
    assertTrue(corridor.isRoutableArea());

    OsmWay door = getClosedPolygon().copy().setTag("indoor", "door").build();
    assertFalse(door.isRoutableArea());
  }

  @Test
  void testIsSteps() {
    OsmWay way = OsmWay.of().build();
    assertFalse(way.isSteps());

    way = OsmWay.of().setTag("highway", "primary").build();
    assertFalse(way.isSteps());

    way = OsmWay.of().setTag("highway", "steps").build();
    assertTrue(way.isSteps());
  }

  @Test
  void testIsStairs() {
    OsmWay way = OsmWay.of().build();
    assertFalse(way.isStairs());

    way = OsmWay.of().setTag("highway", "primary").build();
    assertFalse(way.isStairs());

    way = OsmWay.of().setTag("highway", "steps").build();
    assertTrue(way.isStairs());

    way = way.copy().setTag("conveying", "yes").build();
    assertFalse(way.isStairs());
  }

  @Test
  void wheelchairAccessibleStairs() {
    var osm1 = OsmWay.of().setTag("highway", "steps").build();
    assertFalse(osm1.isWheelchairAccessible());

    // explicitly suitable for wheelchair users, perhaps because of a ramp
    var osm2 = OsmWay.of().setTag("highway", "steps").setTag("wheelchair", "yes").build();
    assertTrue(osm2.isWheelchairAccessible());
  }

  @Test
  void testIsRoundabout() {
    OsmWay way = OsmWay.of().build();
    assertFalse(way.isRoundabout());

    way = OsmWay.of().setTag("junction", "dovetail").build();
    assertFalse(way.isRoundabout());

    way = OsmWay.of().setTag("junction", "roundabout").build();
    assertTrue(way.isRoundabout());
  }

  @Test
  void testIsOneWayDriving() {
    assertEquals(Optional.empty(), OsmWay.of().build().isOneWay("motorcar"));
    assertEquals(
      Optional.empty(),
      OsmWay.of().setTag("oneway", "notatagvalue").build().isOneWay("motorcar")
    );
    assertEquals(Optional.empty(), OsmWay.of().setTag("oneway", "no").build().isOneWay("motorcar"));
    assertEquals(
      Optional.of(FORWARD),
      OsmWay.of().setTag("oneway", "1").build().isOneWay("motorcar")
    );
    assertEquals(
      Optional.of(FORWARD),
      OsmWay.of().setTag("oneway", "true").build().isOneWay("motorcar")
    );
    assertEquals(
      Optional.of(BACKWARD),
      OsmWay.of().setTag("oneway", "-1").build().isOneWay("motorcar")
    );
    assertEquals(
      Optional.of(FORWARD),
      OsmWay.of().setTag("junction", "roundabout").build().isOneWay("motorcar")
    );
    assertEquals(
      Optional.of(FORWARD),
      OsmWay.of().setTag("highway", "motorway").build().isOneWay("motorcar")
    );
  }

  @Test
  void testIsOneWayBicycle() {
    assertEquals(Optional.empty(), OsmWay.of().build().isOneWay("bicycle"));
    assertEquals(
      Optional.empty(),
      OsmWay.of().setTag("oneway", "notatagvalue").build().isOneWay("bicycle")
    );
    assertEquals(Optional.empty(), OsmWay.of().setTag("oneway", "no").build().isOneWay("bicycle"));
    assertEquals(
      Optional.of(FORWARD),
      OsmWay.of().setTag("oneway", "1").build().isOneWay("bicycle")
    );
    assertEquals(
      Optional.of(FORWARD),
      OsmWay.of().setTag("oneway", "true").build().isOneWay("bicycle")
    );
    assertEquals(
      Optional.of(BACKWARD),
      OsmWay.of().setTag("oneway", "-1").build().isOneWay("bicycle")
    );
    assertEquals(
      Optional.of(FORWARD),
      OsmWay.of().setTag("junction", "roundabout").build().isOneWay("bicycle")
    );

    assertEquals(
      Optional.empty(),
      OsmWay.of().setTag("oneway", "yes").setTag("oneway:bicycle", "no").build().isOneWay("bicycle")
    );
    assertEquals(
      Optional.of(FORWARD),
      OsmWay.of().setTag("oneway", "no").setTag("oneway:bicycle", "yes").build().isOneWay("bicycle")
    );
    assertEquals(
      Optional.empty(),
      OsmWay.of()
        .setTag("oneway", "yes")
        .setTag("bicycle:backward", "yes")
        .build()
        .isOneWay("bicycle")
    );
    assertEquals(
      Optional.empty(),
      OsmWay.of().setTag("oneway", "yes").setTag("cycleway", "opposite").build().isOneWay("bicycle")
    );
    assertEquals(
      Optional.empty(),
      OsmWay.of()
        .setTag("oneway", "yes")
        .setTag("cycleway", "opposite_lane")
        .build()
        .isOneWay("bicycle")
    );
  }

  @Test
  void testIsOneWayFoot() {
    assertEquals(Optional.empty(), OsmWay.of().build().isOneWay("foot"));
    assertEquals(
      Optional.empty(),
      OsmWay.of().setTag("oneway", "notatagvalue").build().isOneWay("foot")
    );
    assertEquals(Optional.empty(), OsmWay.of().setTag("oneway", "no").build().isOneWay("foot"));
    assertEquals(Optional.empty(), OsmWay.of().setTag("oneway", "1").build().isOneWay("foot"));
    assertEquals(Optional.empty(), OsmWay.of().setTag("oneway", "true").build().isOneWay("foot"));
    assertEquals(Optional.empty(), OsmWay.of().setTag("oneway", "-1").build().isOneWay("foot"));
    assertEquals(
      Optional.empty(),
      OsmWay.of().setTag("junction", "roundabout").build().isOneWay("foot")
    );

    assertEquals(
      Optional.of(FORWARD),
      OsmWay.of().setTag("oneway:foot", "yes").build().isOneWay("foot")
    );
    assertEquals(
      Optional.of(BACKWARD),
      OsmWay.of().setTag("oneway:foot", "-1").build().isOneWay("foot")
    );
    assertEquals(
      Optional.of(FORWARD),
      OsmWay.of().setTag("highway", "footway").setTag("oneway", "yes").build().isOneWay("foot")
    );
    assertEquals(
      Optional.of(BACKWARD),
      OsmWay.of().setTag("highway", "footway").setTag("oneway", "-1").build().isOneWay("foot")
    );
  }

  @Test
  void testIsOpposableCycleway() {
    OsmWay way = OsmWay.of().build();
    assertFalse(way.isOpposableCycleway());

    way = OsmWay.of().setTag("cycleway", "notatagvalue").build();
    assertFalse(way.isOpposableCycleway());

    way = OsmWay.of().setTag("cycleway", "oppo").build();
    assertFalse(way.isOpposableCycleway());

    way = OsmWay.of().setTag("cycleway", "opposite").build();
    assertTrue(way.isOpposableCycleway());

    way = OsmWay.of().setTag("cycleway", "nope").setTag("cycleway:left", "opposite_side").build();
    assertTrue(way.isOpposableCycleway());
  }

  @Test
  void testIsEscalator() {
    assertFalse(WayTestData.highwayWithCycleLane().isEscalator());

    var escalator = OsmWay.of().setTag("highway", "steps").build();
    assertFalse(escalator.isEscalator());

    escalator = escalator.copy().setTag("conveying", "yes").build();
    assertTrue(escalator.isEscalator());

    escalator = escalator.copy().setTag("conveying", "whoknows?").build();
    assertFalse(escalator.isEscalator());

    escalator = escalator.copy().setTag("conveying", "forward").build();
    assertTrue(escalator.isForwardEscalator());

    escalator = escalator.copy().setTag("conveying", "backward").build();
    assertTrue(escalator.isBackwardEscalator());
  }

  @Test
  void isRelevantForRouting() {
    var way = OsmWay.of().setTag("highway", "residential").build();
    assertTrue(way.isRelevantForRouting());
    way = way.copy().setTag("access", "no").build();
    assertFalse(way.isRelevantForRouting());

    way = OsmWay.of().setTag("amenity", "parking").setTag("area", "yes").build();
    assertFalse(way.isRelevantForRouting());
    way = way.copy().setTag("park_ride", "train").build();
    assertTrue(way.isRelevantForRouting());

    way = OsmWay.of().setTag("amenity", "bicycle_parking").setTag("area", "yes").build();
    assertTrue(way.isRelevantForRouting());

    way = OsmWay.of().setTag("public_transport", "platform").setTag("area", "yes").build();
    assertTrue(way.isRelevantForRouting());
  }

  private OsmWay getClosedPolygon() {
    return OsmWay.of().addNodeRef(1, 2, 3, 1).build();
  }

  private static OsmWay createCrossing(String crossingTag, String crossingValue) {
    return WayTestData.footway()
      .copy()
      .setTag("footway", "crossing")
      .setTag(crossingTag, crossingValue)
      .build();
  }

  @Test
  void footway() {
    assertFalse(WayTestData.highwayPrimary().isFootway());
    assertTrue(WayTestData.footway().isFootway());
  }

  @Test
  void serviceRoad() {
    assertFalse(WayTestData.highwayPrimary().isServiceRoad());

    var way = OsmWay.of().setTag("highway", "service").build();
    assertTrue(way.isServiceRoad());
  }

  @Test
  void motorwayRamp() {
    assertFalse(WayTestData.highwayPrimary().isMotorwayRamp());
    assertFalse(WayTestData.motorway().isMotorwayRamp());
    assertTrue(WayTestData.motorwayRamp().isMotorwayRamp());
  }

  @Test
  void turnLane() {
    assertFalse(WayTestData.highwayTertiary().isTurnLane());

    var namedOneWay = OsmWay.of().setTag("name", "3rd Street").setTag("oneway", "yes").build();
    assertFalse(namedOneWay.isTurnLane());

    var oneWay = WayTestData.highwayTertiary().copy().setTag("oneway", "yes").build();
    assertTrue(oneWay.isTurnLane());
  }

  @ParameterizedTest
  @MethodSource("createRampAsTurnLaneCases")
  void rampAsTurnLane(String turnValue, boolean oneWay, boolean expected) {
    var builder = WayTestData.motorwayRamp().copy();
    if (oneWay) {
      builder.setTag("oneway", "yes");
    }
    builder.setTag("turn:lanes", turnValue);
    var ramp = builder.build();

    assertEquals(
      expected,
      ramp.isTurnLane(),
      String.format(
        "%s-way ramp with '%s' turn lane attribute %s a turn lane.",
        oneWay ? "One" : "Two",
        turnValue,
        expected ? "should be" : "should not be"
      )
    );
  }

  static Stream<Arguments> createRampAsTurnLaneCases() {
    return Stream.of(
      Arguments.of("right", true, true),
      Arguments.of("right", false, false),
      Arguments.of("left", true, true),
      Arguments.of("left", false, false),
      Arguments.of("merge_left", true, false),
      Arguments.of("merge_left", false, false),
      Arguments.of(null, true, false),
      Arguments.of(null, false, false)
    );
  }

  @ParameterizedTest
  @MethodSource("createCrossingCases")
  void crossing(OsmWay way, boolean result) {
    assertEquals(result, way.isCrossing());
  }

  static Stream<Arguments> createCrossingCases() {
    return Stream.of(
      Arguments.of(WayTestData.footway(), false),
      Arguments.of(WayTestData.footwaySidewalk(), false),
      Arguments.of(createCrossing("crossing", "marked"), true),
      Arguments.of(createCrossing("crossing", "other"), true),
      Arguments.of(createCrossing("crossing:markings", "yes"), true),
      Arguments.of(createCrossing("crossing:markings", "marking-details"), true),
      Arguments.of(createCrossing("crossing:markings", null), true),
      Arguments.of(createCrossing("crossing:markings", "no"), true)
    );
  }

  @Test
  void adjacentTo() {
    final long nodeId1 = 10001L;
    final long nodeId2 = 20002L;
    final long sharedNodeId = 30003L;

    OsmWay way1 = OsmWay.of().build();
    OsmWay way2 = OsmWay.of().build();
    assertFalse(way1.isAdjacentTo(way2));

    way1 = way1.copy().addNodeRef(sharedNodeId, nodeId1).build();
    way2 = way2.copy().addNodeRef(nodeId2).build();
    assertFalse(way1.isAdjacentTo(way2));

    way2 = way2.copy().addNodeRef(sharedNodeId).build();
    assertTrue(way1.isAdjacentTo(way2));
  }
}
