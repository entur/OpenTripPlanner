package org.opentripplanner.osm.wayproperty;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.opentripplanner.graph_builder.module.osm.StreetTraversalPermissionPair;
import org.opentripplanner.osm.model.OsmWay;
import org.opentripplanner.street.model.StreetTraversalPermission;

class OverridePermissionsTest {

  public static final WayPropertySet WAY_PROPERTY_SET = WayPropertySet.of().build();

  /**
   * Tests if cars can drive on unclassified highways with bicycleDesignated
   * <p>
   * Check for bug #1878 and PR #1880
   */
  @Test
  void testCarPermission() {
    OsmWay way = OsmWay.of().setTag("highway", "unclassified").build();

    var permissionPair = getWayProperties(way);
    assertTrue(permissionPair.main().allows(StreetTraversalPermission.ALL));

    way = way.copy().setTag("bicycle", "designated").build();
    permissionPair = getWayProperties(way);
    assertTrue(permissionPair.main().allows(StreetTraversalPermission.ALL));
  }

  /**
   * Tests that motorcar/bicycle/foot private don't add permissions but yes add permission if access
   * is no
   */
  @Test
  void testMotorCarTagAllowedPermissions() {
    OsmWay way = OsmWay.of().setTag("highway", "residential").build();
    var permissionPair = getWayProperties(way);
    assertTrue(permissionPair.main().allows(StreetTraversalPermission.ALL));

    way = way.copy().setTag("access", "no").build();
    permissionPair = getWayProperties(way);
    assertTrue(permissionPair.main().allowsNothing());

    way = way
      .copy()
      .setTag("motorcar", "private")
      .setTag("bicycle", "private")
      .setTag("foot", "private")
      .build();
    permissionPair = getWayProperties(way);
    assertTrue(permissionPair.main().allowsNothing());

    way = way.copy().setTag("motorcar", "yes").build();
    permissionPair = getWayProperties(way);
    assertTrue(permissionPair.main().allows(StreetTraversalPermission.CAR));

    way = way.copy().setTag("bicycle", "yes").build();
    permissionPair = getWayProperties(way);
    assertTrue(permissionPair.main().allows(StreetTraversalPermission.BICYCLE_AND_CAR));

    way = way.copy().setTag("foot", "yes").build();
    permissionPair = getWayProperties(way);
    assertTrue(permissionPair.main().allows(StreetTraversalPermission.ALL));
  }

  /**
   * Tests that motorcar/bicycle/foot private don't add permissions but no remove permission if
   * access is yes
   */
  @Test
  void testMotorCarTagDeniedPermissions() {
    OsmWay way = OsmWay.of().setTag("highway", "residential").build();
    var permissionPair = getWayProperties(way);
    assertTrue(permissionPair.main().allows(StreetTraversalPermission.ALL));

    way = way.copy().setTag("motorcar", "no").build();
    permissionPair = getWayProperties(way);
    assertTrue(permissionPair.main().allows(StreetTraversalPermission.PEDESTRIAN_AND_BICYCLE));

    way = way.copy().setTag("bicycle", "no").build();
    permissionPair = getWayProperties(way);
    assertTrue(permissionPair.main().allows(StreetTraversalPermission.PEDESTRIAN));

    way = way.copy().setTag("foot", "no").build();
    permissionPair = getWayProperties(way);
    assertTrue(permissionPair.main().allowsNothing());
  }

  /**
   * Tests that motor_vehicle/bicycle/foot private don't add permissions but yes add permission if
   * access is no
   * <p>
   * Support for motor_vehicle was added in #1881
   */
  @Test
  void testMotorVehicleTagAllowedPermissions() {
    OsmWay way = OsmWay.of().setTag("highway", "residential").build();
    var permissionPair = getWayProperties(way);
    assertTrue(permissionPair.main().allows(StreetTraversalPermission.ALL));

    way = way.copy().setTag("access", "no").build();
    permissionPair = getWayProperties(way);
    assertTrue(permissionPair.main().allowsNothing());

    way = way
      .copy()
      .setTag("motor_vehicle", "private")
      .setTag("bicycle", "private")
      .setTag("foot", "private")
      .build();
    permissionPair = getWayProperties(way);
    assertTrue(permissionPair.main().allowsNothing());

    way = way.copy().setTag("motor_vehicle", "yes").build();
    permissionPair = getWayProperties(way);
    assertTrue(permissionPair.main().allows(StreetTraversalPermission.CAR));

    way = way.copy().setTag("bicycle", "yes").build();
    permissionPair = getWayProperties(way);
    assertTrue(permissionPair.main().allows(StreetTraversalPermission.BICYCLE_AND_CAR));

    way = way.copy().setTag("foot", "yes").build();
    permissionPair = getWayProperties(way);
    assertTrue(permissionPair.main().allows(StreetTraversalPermission.ALL));
  }

  /**
   * Tests that motor_vehicle/bicycle/foot private don't add permissions but no remove permission if
   * access is yes
   * <p>
   * Support for motor_vehicle was added in #1881
   */
  @Test
  void testMotorVehicleTagDeniedPermissions() {
    OsmWay way = OsmWay.of().setTag("highway", "residential").build();
    var permissionPair = getWayProperties(way);
    assertTrue(permissionPair.main().allows(StreetTraversalPermission.ALL));

    way = way.copy().setTag("motor_vehicle", "no").build();
    permissionPair = getWayProperties(way);
    assertTrue(permissionPair.main().allows(StreetTraversalPermission.PEDESTRIAN_AND_BICYCLE));

    way = way.copy().setTag("bicycle", "no").build();
    permissionPair = getWayProperties(way);
    assertTrue(permissionPair.main().allows(StreetTraversalPermission.PEDESTRIAN));

    way = way.copy().setTag("foot", "no").build();
    permissionPair = getWayProperties(way);
    assertTrue(permissionPair.main().allowsNothing());
  }

  private StreetTraversalPermissionPair getWayProperties(OsmWay way) {
    WayPropertiesPair wayData = WAY_PROPERTY_SET.getDataForWay(way);

    return new StreetTraversalPermissionPair(
      wayData.forward().getPermission(),
      wayData.backward().getPermission()
    );
  }
}
