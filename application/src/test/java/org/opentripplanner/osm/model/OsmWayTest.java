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
    var entity = OsmWay.of().addTag("foo", "bar").addTag("FOO", "baz").build();
    assertEquals("baz", entity.getTag("foo"));
  }

  @Test
  void testIsBicycleDismountForced() {
    OsmWay way = OsmWay.of().build();
    assertFalse(way.isBicycleDismountForced());

    way = OsmWay.of().addTag("bicycle", "dismount").build();
    assertTrue(way.isBicycleDismountForced());
  }

  @Test
  void testAreaMustContain3Nodes() {
    OsmWay way = OsmWay.of().addTag("area", "yes").build();
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
    OsmWay platform = getClosedPolygon().copy().addTag("public_transport", "platform").build();
    assertTrue(platform.isRoutableArea());
    platform = platform.copy().addTag("area", "no").build();
    assertFalse(platform.isRoutableArea());

    OsmWay roundabout = getClosedPolygon().copy().addTag("highway", "roundabout").build();
    assertFalse(roundabout.isRoutableArea());

    OsmWay pedestrian = getClosedPolygon().copy().addTag("highway", "pedestrian").build();
    assertFalse(pedestrian.isRoutableArea());
    pedestrian = pedestrian.copy().addTag("area", "yes").build();
    assertTrue(pedestrian.isRoutableArea());

    OsmWay indoorArea = getClosedPolygon().copy().addTag("indoor", "area").build();
    assertTrue(indoorArea.isRoutableArea());

    OsmWay bikeParking = getClosedPolygon().copy().addTag("amenity", "bicycle_parking").build();
    assertTrue(bikeParking.isRoutableArea());

    OsmWay corridor = getClosedPolygon().copy().addTag("indoor", "corridor").build();
    assertTrue(corridor.isRoutableArea());

    OsmWay door = getClosedPolygon().copy().addTag("indoor", "door").build();
    assertFalse(door.isRoutableArea());
  }

  @Test
  void testIsSteps() {
    OsmWay way = OsmWay.of().build();
    assertFalse(way.isSteps());

    way = OsmWay.of().addTag("highway", "primary").build();
    assertFalse(way.isSteps());

    way = OsmWay.of().addTag("highway", "steps").build();
    assertTrue(way.isSteps());
  }

  @Test
  void testIsStairs() {
    OsmWay way = OsmWay.of().build();
    assertFalse(way.isStairs());

    way = OsmWay.of().addTag("highway", "primary").build();
    assertFalse(way.isStairs());

    way = OsmWay.of().addTag("highway", "steps").build();
    assertTrue(way.isStairs());

    way = way.copy().addTag("conveying", "yes").build();
    assertFalse(way.isStairs());
  }

  @Test
  void wheelchairAccessibleStairs() {
    var osm1 = OsmWay.of().addTag("highway", "steps").build();
    assertFalse(osm1.isWheelchairAccessible());

    // explicitly suitable for wheelchair users, perhaps because of a ramp
    var osm2 = OsmWay.of().addTag("highway", "steps").addTag("wheelchair", "yes").build();
    assertTrue(osm2.isWheelchairAccessible());
  }

  @Test
  void testIsRoundabout() {
    OsmWay way = OsmWay.of().build();
    assertFalse(way.isRoundabout());

    way = OsmWay.of().addTag("junction", "dovetail").build();
    assertFalse(way.isRoundabout());

    way = OsmWay.of().addTag("junction", "roundabout").build();
    assertTrue(way.isRoundabout());
  }

  @Test
  void testIsOneWayDriving() {
    assertEquals(Optional.empty(), OsmWay.of().build().isOneWay("motorcar"));
    assertEquals(
      Optional.empty(),
      OsmWay.of().addTag("oneway", "notatagvalue").build().isOneWay("motorcar")
    );
    assertEquals(Optional.empty(), OsmWay.of().addTag("oneway", "no").build().isOneWay("motorcar"));
    assertEquals(
      Optional.of(FORWARD),
      OsmWay.of().addTag("oneway", "1").build().isOneWay("motorcar")
    );
    assertEquals(
      Optional.of(FORWARD),
      OsmWay.of().addTag("oneway", "true").build().isOneWay("motorcar")
    );
    assertEquals(
      Optional.of(BACKWARD),
      OsmWay.of().addTag("oneway", "-1").build().isOneWay("motorcar")
    );
    assertEquals(
      Optional.of(FORWARD),
      OsmWay.of().addTag("junction", "roundabout").build().isOneWay("motorcar")
    );
    assertEquals(
      Optional.of(FORWARD),
      OsmWay.of().addTag("highway", "motorway").build().isOneWay("motorcar")
    );
  }

  @Test
  void testIsOneWayBicycle() {
    assertEquals(Optional.empty(), OsmWay.of().build().isOneWay("bicycle"));
    assertEquals(
      Optional.empty(),
      OsmWay.of().addTag("oneway", "notatagvalue").build().isOneWay("bicycle")
    );
    assertEquals(Optional.empty(), OsmWay.of().addTag("oneway", "no").build().isOneWay("bicycle"));
    assertEquals(
      Optional.of(FORWARD),
      OsmWay.of().addTag("oneway", "1").build().isOneWay("bicycle")
    );
    assertEquals(
      Optional.of(FORWARD),
      OsmWay.of().addTag("oneway", "true").build().isOneWay("bicycle")
    );
    assertEquals(
      Optional.of(BACKWARD),
      OsmWay.of().addTag("oneway", "-1").build().isOneWay("bicycle")
    );
    assertEquals(
      Optional.of(FORWARD),
      OsmWay.of().addTag("junction", "roundabout").build().isOneWay("bicycle")
    );

    assertEquals(
      Optional.empty(),
      OsmWay.of().addTag("oneway", "yes").addTag("oneway:bicycle", "no").build().isOneWay("bicycle")
    );
    assertEquals(
      Optional.of(FORWARD),
      OsmWay.of().addTag("oneway", "no").addTag("oneway:bicycle", "yes").build().isOneWay("bicycle")
    );
    assertEquals(
      Optional.empty(),
      OsmWay.of()
        .addTag("oneway", "yes")
        .addTag("bicycle:backward", "yes")
        .build()
        .isOneWay("bicycle")
    );
    assertEquals(
      Optional.empty(),
      OsmWay.of().addTag("oneway", "yes").addTag("cycleway", "opposite").build().isOneWay("bicycle")
    );
    assertEquals(
      Optional.empty(),
      OsmWay.of()
        .addTag("oneway", "yes")
        .addTag("cycleway", "opposite_lane")
        .build()
        .isOneWay("bicycle")
    );
  }

  @Test
  void testIsOneWayFoot() {
    assertEquals(Optional.empty(), OsmWay.of().build().isOneWay("foot"));
    assertEquals(
      Optional.empty(),
      OsmWay.of().addTag("oneway", "notatagvalue").build().isOneWay("foot")
    );
    assertEquals(Optional.empty(), OsmWay.of().addTag("oneway", "no").build().isOneWay("foot"));
    assertEquals(Optional.empty(), OsmWay.of().addTag("oneway", "1").build().isOneWay("foot"));
    assertEquals(Optional.empty(), OsmWay.of().addTag("oneway", "true").build().isOneWay("foot"));
    assertEquals(Optional.empty(), OsmWay.of().addTag("oneway", "-1").build().isOneWay("foot"));
    assertEquals(
      Optional.empty(),
      OsmWay.of().addTag("junction", "roundabout").build().isOneWay("foot")
    );

    assertEquals(
      Optional.of(FORWARD),
      OsmWay.of().addTag("oneway:foot", "yes").build().isOneWay("foot")
    );
    assertEquals(
      Optional.of(BACKWARD),
      OsmWay.of().addTag("oneway:foot", "-1").build().isOneWay("foot")
    );
    assertEquals(
      Optional.of(FORWARD),
      OsmWay.of().addTag("highway", "footway").addTag("oneway", "yes").build().isOneWay("foot")
    );
    assertEquals(
      Optional.of(BACKWARD),
      OsmWay.of().addTag("highway", "footway").addTag("oneway", "-1").build().isOneWay("foot")
    );
  }

  @Test
  void testIsOpposableCycleway() {
    OsmWay way = OsmWay.of().build();
    assertFalse(way.isOpposableCycleway());

    way = OsmWay.of().addTag("cycleway", "notatagvalue").build();
    assertFalse(way.isOpposableCycleway());

    way = OsmWay.of().addTag("cycleway", "oppo").build();
    assertFalse(way.isOpposableCycleway());

    way = OsmWay.of().addTag("cycleway", "opposite").build();
    assertTrue(way.isOpposableCycleway());

    way = OsmWay.of().addTag("cycleway", "nope").addTag("cycleway:left", "opposite_side").build();
    assertTrue(way.isOpposableCycleway());
  }

  @Test
  void testIsEscalator() {
    assertFalse(WayTestData.highwayWithCycleLane().isEscalator());

    var escalator = OsmWay.of().addTag("highway", "steps").build();
    assertFalse(escalator.isEscalator());

    escalator = escalator.copy().addTag("conveying", "yes").build();
    assertTrue(escalator.isEscalator());

    escalator = escalator.copy().addTag("conveying", "whoknows?").build();
    assertFalse(escalator.isEscalator());

    escalator = escalator.copy().addTag("conveying", "forward").build();
    assertTrue(escalator.isForwardEscalator());

    escalator = escalator.copy().addTag("conveying", "backward").build();
    assertTrue(escalator.isBackwardEscalator());
  }

  @Test
  void isRelevantForRouting() {
    var way = OsmWay.of().addTag("highway", "residential").build();
    assertTrue(way.isRelevantForRouting());
    way = way.copy().addTag("access", "no").build();
    assertFalse(way.isRelevantForRouting());

    way = OsmWay.of().addTag("amenity", "parking").addTag("area", "yes").build();
    assertFalse(way.isRelevantForRouting());
    way = way.copy().addTag("park_ride", "train").build();
    assertTrue(way.isRelevantForRouting());

    way = OsmWay.of().addTag("amenity", "bicycle_parking").addTag("area", "yes").build();
    assertTrue(way.isRelevantForRouting());

    way = OsmWay.of().addTag("public_transport", "platform").addTag("area", "yes").build();
    assertTrue(way.isRelevantForRouting());
  }

  private OsmWay getClosedPolygon() {
    return OsmWay.of().addNodeRef(1, 2, 3, 1).build();
  }

  private static OsmWay createCrossing(String crossingTag, String crossingValue) {
    return WayTestData.footway()
      .copy()
      .addTag("footway", "crossing")
      .addTag(crossingTag, crossingValue)
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

    var way = OsmWay.of().addTag("highway", "service").build();
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

    var namedOneWay = OsmWay.of().addTag("name", "3rd Street").addTag("oneway", "yes").build();
    assertFalse(namedOneWay.isTurnLane());

    var oneWay = WayTestData.highwayTertiary().copy().addTag("oneway", "yes").build();
    assertTrue(oneWay.isTurnLane());
  }

  @ParameterizedTest
  @MethodSource("createRampAsTurnLaneCases")
  void rampAsTurnLane(String turnValue, boolean oneWay, boolean expected) {
    var builder = WayTestData.motorwayRamp().copy();
    if (oneWay) {
      builder.addTag("oneway", "yes");
    }
    builder.addTag("turn:lanes", turnValue);
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
