package org.opentripplanner.osm.tagmapping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opentripplanner.street.model.StreetTraversalPermission.ALL;
import static org.opentripplanner.street.model.StreetTraversalPermission.CAR;

import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.opentripplanner.osm.model.OsmWithTags;
import org.opentripplanner.osm.wayproperty.WayPropertySet;
import org.opentripplanner.osm.wayproperty.specifier.WayTestData;

class OsmTagMapperTest {

  @Test
  void isMotorThroughTrafficExplicitlyDisallowed() {
    OsmWithTags o = new OsmWithTags();
    OsmTagMapper osmTagMapper = new OsmTagMapper();

    assertFalse(osmTagMapper.isMotorVehicleThroughTrafficExplicitlyDisallowed(o));

    o.addTag("access", "something");
    assertFalse(osmTagMapper.isMotorVehicleThroughTrafficExplicitlyDisallowed(o));

    o.addTag("access", "destination");
    assertTrue(osmTagMapper.isMotorVehicleThroughTrafficExplicitlyDisallowed(o));

    o.addTag("access", "private");
    assertTrue(osmTagMapper.isMotorVehicleThroughTrafficExplicitlyDisallowed(o));

    assertTrue(
      osmTagMapper.isMotorVehicleThroughTrafficExplicitlyDisallowed(
        way("motor_vehicle", "destination")
      )
    );
  }

  @Test
  void isBicycleThroughTrafficExplicitlyDisallowed() {
    OsmTagMapper osmTagMapper = new OsmTagMapper();
    assertTrue(
      osmTagMapper.isBicycleThroughTrafficExplicitlyDisallowed(way("bicycle", "destination"))
    );
    assertTrue(
      osmTagMapper.isBicycleThroughTrafficExplicitlyDisallowed(way("access", "destination"))
    );
  }

  @Test
  void isWalkThroughTrafficExplicitlyDisallowed() {
    OsmTagMapper osmTagMapper = new OsmTagMapper();
    assertTrue(osmTagMapper.isWalkThroughTrafficExplicitlyDisallowed(way("foot", "destination")));
    assertTrue(osmTagMapper.isWalkThroughTrafficExplicitlyDisallowed(way("access", "destination")));
  }

  @Test
  void testAccessNo() {
    OsmWithTags tags = new OsmWithTags();
    OsmTagMapper osmTagMapper = new OsmTagMapper();

    tags.addTag("access", "no");

    assertTrue(osmTagMapper.isMotorVehicleThroughTrafficExplicitlyDisallowed(tags));
    assertTrue(osmTagMapper.isBicycleThroughTrafficExplicitlyDisallowed(tags));
    assertTrue(osmTagMapper.isWalkThroughTrafficExplicitlyDisallowed(tags));
  }

  @Test
  void testAccessPrivate() {
    OsmWithTags tags = new OsmWithTags();
    OsmTagMapper osmTagMapper = new OsmTagMapper();

    tags.addTag("access", "private");

    assertTrue(osmTagMapper.isMotorVehicleThroughTrafficExplicitlyDisallowed(tags));
    assertTrue(osmTagMapper.isBicycleThroughTrafficExplicitlyDisallowed(tags));
    assertTrue(osmTagMapper.isWalkThroughTrafficExplicitlyDisallowed(tags));
  }

  @Test
  void testFootModifier() {
    OsmWithTags tags = new OsmWithTags();
    OsmTagMapper osmTagMapper = new OsmTagMapper();

    tags.addTag("access", "private");
    tags.addTag("foot", "yes");

    assertTrue(osmTagMapper.isMotorVehicleThroughTrafficExplicitlyDisallowed(tags));
    assertTrue(osmTagMapper.isBicycleThroughTrafficExplicitlyDisallowed(tags));
    assertFalse(osmTagMapper.isWalkThroughTrafficExplicitlyDisallowed(tags));
  }

  @Test
  void testVehicleDenied() {
    OsmWithTags tags = new OsmWithTags();
    OsmTagMapper osmTagMapper = new OsmTagMapper();

    tags.addTag("vehicle", "destination");

    assertTrue(osmTagMapper.isMotorVehicleThroughTrafficExplicitlyDisallowed(tags));
    assertTrue(osmTagMapper.isBicycleThroughTrafficExplicitlyDisallowed(tags));
    assertFalse(osmTagMapper.isWalkThroughTrafficExplicitlyDisallowed(tags));
  }

  @Test
  void testVehicleDeniedMotorVehiclePermissive() {
    OsmWithTags tags = new OsmWithTags();
    OsmTagMapper osmTagMapper = new OsmTagMapper();

    tags.addTag("vehicle", "destination");
    tags.addTag("motor_vehicle", "designated");

    assertFalse(osmTagMapper.isMotorVehicleThroughTrafficExplicitlyDisallowed(tags));
    assertTrue(osmTagMapper.isBicycleThroughTrafficExplicitlyDisallowed(tags));
    assertFalse(osmTagMapper.isWalkThroughTrafficExplicitlyDisallowed(tags));
  }

  @Test
  void testVehicleDeniedBicyclePermissive() {
    OsmWithTags tags = new OsmWithTags();
    OsmTagMapper osmTagMapper = new OsmTagMapper();

    tags.addTag("vehicle", "destination");
    tags.addTag("bicycle", "designated");

    assertTrue(osmTagMapper.isMotorVehicleThroughTrafficExplicitlyDisallowed(tags));
    assertFalse(osmTagMapper.isBicycleThroughTrafficExplicitlyDisallowed(tags));
    assertFalse(osmTagMapper.isWalkThroughTrafficExplicitlyDisallowed(tags));
  }

  @Test
  void testMotorcycleModifier() {
    OsmWithTags tags = new OsmWithTags();
    OsmTagMapper osmTagMapper = new OsmTagMapper();

    tags.addTag("access", "private");
    tags.addTag("motor_vehicle", "yes");

    assertFalse(osmTagMapper.isMotorVehicleThroughTrafficExplicitlyDisallowed(tags));
    assertTrue(osmTagMapper.isBicycleThroughTrafficExplicitlyDisallowed(tags));
    assertTrue(osmTagMapper.isWalkThroughTrafficExplicitlyDisallowed(tags));
  }

  @Test
  void testBicycleModifier() {
    OsmWithTags tags = new OsmWithTags();
    OsmTagMapper osmTagMapper = new OsmTagMapper();

    tags.addTag("access", "private");
    tags.addTag("bicycle", "yes");

    assertTrue(osmTagMapper.isMotorVehicleThroughTrafficExplicitlyDisallowed(tags));
    assertFalse(osmTagMapper.isBicycleThroughTrafficExplicitlyDisallowed(tags));
    assertTrue(osmTagMapper.isWalkThroughTrafficExplicitlyDisallowed(tags));
  }

  @Test
  void testBicyclePermissive() {
    OsmWithTags tags = new OsmWithTags();
    OsmTagMapper osmTagMapper = new OsmTagMapper();

    tags.addTag("access", "private");
    tags.addTag("bicycle", "permissive");

    assertTrue(osmTagMapper.isMotorVehicleThroughTrafficExplicitlyDisallowed(tags));
    assertFalse(osmTagMapper.isBicycleThroughTrafficExplicitlyDisallowed(tags));
    assertTrue(osmTagMapper.isWalkThroughTrafficExplicitlyDisallowed(tags));
  }

  public static List<OsmWithTags> roadCases() {
    return List.of(
      WayTestData.carTunnel(),
      WayTestData.southwestMayoStreet(),
      WayTestData.southeastLaBonitaWay(),
      WayTestData.fiveLanes(),
      WayTestData.highwayTertiary()
    );
  }

  @ParameterizedTest
  @MethodSource("roadCases")
  void motorroad(OsmWithTags way) {
    final WayPropertySet wps = wayProperySet();

    assertEquals(ALL, wps.getDataForWay(way).getPermission());

    way.addTag("motorroad", "yes");
    assertEquals(CAR, wps.getDataForWay(way).getPermission());
  }

  @Test
  void corridorName() {
    final WayPropertySet wps = wayProperySet();
    var way = way("highway", "corridor");
    assertEquals("corridor", wps.getCreativeNameForWay(way).toString());
    assertEquals("Korridor", wps.getCreativeNameForWay(way).toString(Locale.GERMANY));
    assertEquals("käytävä", wps.getCreativeNameForWay(way).toString(Locale.of("FI")));
  }

  public OsmWithTags way(String key, String value) {
    var way = new OsmWithTags();
    way.addTag(key, value);
    return way;
  }

  private static WayPropertySet wayProperySet() {
    OsmTagMapper osmTagMapper = new OsmTagMapper();
    WayPropertySet wps = new WayPropertySet();
    osmTagMapper.populateProperties(wps);
    return wps;
  }
}
