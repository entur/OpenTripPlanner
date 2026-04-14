package org.opentripplanner.osm.tagmapping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opentripplanner.street.model.StreetTraversalPermission.ALL;
import static org.opentripplanner.street.model.StreetTraversalPermission.CAR;
import static org.opentripplanner.street.model.StreetTraversalPermission.NONE;
import static org.opentripplanner.street.model.StreetTraversalPermission.PEDESTRIAN;
import static org.opentripplanner.street.model.StreetTraversalPermission.PEDESTRIAN_AND_BICYCLE;

import org.junit.jupiter.api.Test;
import org.opentripplanner.osm.model.OsmWay;
import org.opentripplanner.osm.wayproperty.WayProperties;
import org.opentripplanner.osm.wayproperty.WayPropertySet;
import org.opentripplanner.osm.wayproperty.specifier.WayTestData;

class FinlandMapperTest {

  private final OsmTagMapper mapper = new FinlandMapper();
  private final WayPropertySet wps = mapper.buildWayPropertySet();
  private static final float EPSILON = 0.01f;

  /**
   * Test that bike and walk safety factors are calculated accurately
   */
  @Test
  void testSafety() {
    var primaryWay = OsmWay.of().addTag("highway", "primary").addTag("oneway", "no").build();
    var livingStreetWay = OsmWay.of().addTag("highway", "living_street").build();
    var footway = OsmWay.of().addTag("highway", "footway").build();
    var sidewalk = OsmWay.of().addTag("footway", "sidewalk").addTag("highway", "footway").build();
    var segregatedCycleway = OsmWay.of()
      .addTag("segregated", "yes")
      .addTag("highway", "cycleway")
      .build();
    var tunnel = OsmWay.of().addTag("tunnel", "yes").addTag("highway", "footway").build();
    var bridge = OsmWay.of().addTag("bridge", "yes").addTag("highway", "footway").build();
    var footwayCrossing = OsmWay.of()
      .addTag("footway", "crossing")
      .addTag("highway", "footway")
      .build();
    var footwayCrossingWithTrafficLights = OsmWay.of()
      .addTag("footway", "crossing")
      .addTag("highway", "footway")
      .addTag("crossing", "traffic_signals")
      .build();
    var cyclewayCrossing = OsmWay.of()
      .addTag("cycleway", "crossing")
      .addTag("highway", "cycleway")
      .build();
    var cyclewayFootwayCrossing = OsmWay.of()
      .addTag("footway", "crossing")
      .addTag("highway", "cycleway")
      .build();
    var cyclewayCrossingWithTrafficLights = OsmWay.of()
      .addTag("cycleway", "crossing")
      .addTag("highway", "cycleway")
      .addTag("crossing", "traffic_signals")
      .build();
    var cyclewayFootwayCrossingWithTrafficLights = OsmWay.of()
      .addTag("footway", "crossing")
      .addTag("highway", "cycleway")
      .addTag("crossing", "traffic_signals")
      .build();
    var cyclewaySegregatedCrossing = OsmWay.of()
      .addTag("cycleway", "crossing")
      .addTag("segregated", "yes")
      .addTag("highway", "cycleway")
      .build();
    var cyclewaySegregatedFootwayCrossing = OsmWay.of()
      .addTag("footway", "crossing")
      .addTag("segregated", "yes")
      .addTag("highway", "cycleway")
      .build();
    var cyclewaySegregatedCrossingWithTrafficLights = OsmWay.of()
      .addTag("cycleway", "crossing")
      .addTag("segregated", "yes")
      .addTag("highway", "cycleway")
      .addTag("crossing", "traffic_signals")
      .build();
    var cyclewaySegregatedFootwayCrossingWithTrafficLights = OsmWay.of()
      .addTag("footway", "crossing")
      .addTag("segregated", "yes")
      .addTag("highway", "cycleway")
      .addTag("crossing", "traffic_signals")
      .build();
    assertEquals(2.06, wps.getDataForWay(primaryWay).forward().bicycleSafety(), EPSILON);
    // way with high speed limit, has higher walk safety factor
    assertEquals(1.8, wps.getDataForWay(primaryWay).forward().walkSafety(), EPSILON);
    assertEquals(1.8, wps.getDataForWay(primaryWay).backward().walkSafety(), EPSILON);
    // way with low speed limit, has lower walk safety factor
    assertEquals(1.45, wps.getDataForWay(livingStreetWay).forward().walkSafety(), EPSILON);
    assertEquals(1.1, wps.getDataForWay(footway).forward().walkSafety(), EPSILON);
    assertEquals(1.1, wps.getDataForWay(sidewalk).forward().walkSafety(), EPSILON);
    assertEquals(1.1, wps.getDataForWay(segregatedCycleway).forward().walkSafety(), EPSILON);
    assertEquals(1.0, wps.getDataForWay(tunnel).forward().walkSafety(), EPSILON);
    assertEquals(1.0, wps.getDataForWay(bridge).forward().walkSafety(), EPSILON);
    assertEquals(1.2, wps.getDataForWay(footwayCrossing).forward().walkSafety(), EPSILON);
    assertEquals(
      1.1,
      wps.getDataForWay(footwayCrossingWithTrafficLights).forward().walkSafety(),
      EPSILON
    );
    assertEquals(1.25, wps.getDataForWay(cyclewayCrossing).forward().walkSafety(), EPSILON);
    assertEquals(1.25, wps.getDataForWay(cyclewayFootwayCrossing).forward().walkSafety(), EPSILON);
    assertEquals(
      1.15,
      wps.getDataForWay(cyclewayCrossingWithTrafficLights).forward().walkSafety(),
      EPSILON
    );
    assertEquals(
      1.15,
      wps.getDataForWay(cyclewayFootwayCrossingWithTrafficLights).forward().walkSafety(),
      EPSILON
    );
    assertEquals(
      1.2,
      wps.getDataForWay(cyclewaySegregatedCrossing).forward().walkSafety(),
      EPSILON
    );
    assertEquals(
      1.2,
      wps.getDataForWay(cyclewaySegregatedFootwayCrossing).forward().walkSafety(),
      EPSILON
    );
    assertEquals(
      1.1,
      wps.getDataForWay(cyclewaySegregatedCrossingWithTrafficLights).forward().walkSafety(),
      EPSILON
    );
    assertEquals(
      1.1,
      wps.getDataForWay(cyclewaySegregatedFootwayCrossingWithTrafficLights).forward().walkSafety(),
      EPSILON
    );
  }

  @Test
  void testSafetyWithMixins() {
    var wayWithMixins = OsmWay.of()
      // highway=service has no custom bicycle or walk safety
      .addTag("highway", "unclassified")
      // surface has mixin bicycle safety of 1.3 but no walk safety
      .addTag("surface", "metal")
      .build();
    // 1.0 * 1.3 = 1.3
    assertEquals(1.3, wps.getDataForWay(wayWithMixins).forward().bicycleSafety(), EPSILON);
    // 1.6 is the default walk safety for a way with ALL permissions and speed limit > 35 and <= 60 kph
    assertEquals(1.6, wps.getDataForWay(wayWithMixins).forward().walkSafety(), EPSILON);

    var wayWithMixinsAndCustomSafety = OsmWay.of()
      // highway=service has custom bicycle safety of 1.1 but no custom walk safety
      .addTag("highway", "service")
      // surface has mixin bicycle safety of 1.3 but no walk safety
      .addTag("surface", "metal")
      .build();
    // 1.1 * 1.3 = 1.43
    assertEquals(
      1.43,
      wps.getDataForWay(wayWithMixinsAndCustomSafety).forward().bicycleSafety(),
      EPSILON
    );
    // 1.6 is the default walk safety for a way with ALL permissions and speed limit <= 35 kph
    assertEquals(
      1.45,
      wps.getDataForWay(wayWithMixinsAndCustomSafety).forward().walkSafety(),
      EPSILON
    );
  }

  @Test
  void testUseSidePath() {
    var wayWithBicycleSidePath = OsmWay.of().addTag("bicycle", "use_sidepath").build();
    assertEquals(9, wps.getDataForWay(wayWithBicycleSidePath).forward().walkSafety(), EPSILON);
    var wayWithFootSidePath = OsmWay.of().addTag("foot", "use_sidepath").build();
    assertEquals(9, wps.getDataForWay(wayWithFootSidePath).forward().walkSafety(), EPSILON);
  }

  @Test
  void testMaxCarSpeed() {
    assertEquals(33.34, wps.maxPossibleCarSpeed(), EPSILON);
    assertEquals(22.21, wps.defaultCarSpeed(), EPSILON);
  }

  @Test
  void testTagMapping() {
    WayProperties wayData;

    var way = OsmWay.of().addTag("highway", "unclassified").addTag("seasonal", "winter").build();
    wayData = wps.getDataForEntity(way);
    assertEquals(wayData.getPermission(), NONE);

    way = OsmWay.of().addTag("highway", "trunk").addTag("ice_road", "yes").build();
    wayData = wps.getDataForEntity(way);
    assertEquals(wayData.getPermission(), NONE);

    way = OsmWay.of().addTag("highway", "track").addTag("winter_road", "yes").build();
    wayData = wps.getDataForEntity(way);
    assertEquals(wayData.getPermission(), NONE);
  }

  @Test
  void testWalkingAllowedOnCycleway() {
    assertEquals(
      PEDESTRIAN_AND_BICYCLE,
      wps.getDataForEntity(WayTestData.cycleway()).getPermission()
    );
  }

  @Test
  void testCyclingAllowedOnPedestrianAreas() {
    assertEquals(
      PEDESTRIAN_AND_BICYCLE,
      wps.getDataForEntity(WayTestData.pedestrianArea()).getPermission()
    );
  }

  /**
   * Test that biking is not allowed in footway areas and transit platforms
   */
  @Test
  void testArea() {
    WayProperties wayData;

    var way = OsmWay.of().addTag("highway", "footway").addTag("area", "yes").build();
    wayData = wps.getDataForEntity(way);
    assertEquals(PEDESTRIAN, wayData.getPermission());

    way = OsmWay.of().addTag("public_transport", "platform").addTag("area", "yes").build();
    wayData = wps.getDataForEntity(way);
    assertEquals(PEDESTRIAN, wayData.getPermission());
    way = way.copy().addTag("bicycle", "yes").build();
    wayData = wps.getDataForEntity(way);
    assertEquals(PEDESTRIAN_AND_BICYCLE, wayData.getPermission());
  }

  @Test
  void serviceNoThroughTraffic() {
    var way = OsmWay.of().addTag("highway", "residential").addTag("service", "driveway").build();
    assertTrue(mapper.isMotorVehicleThroughTrafficExplicitlyDisallowed(way));
  }

  @Test
  void motorroad() {
    var way = WayTestData.carTunnel();
    assertEquals(ALL, wps.getDataForWay(way).forward().getPermission());
    way = way.copy().addTag("motorroad", "yes").build();
    assertEquals(CAR, wps.getDataForWay(way).forward().getPermission());
  }
}
