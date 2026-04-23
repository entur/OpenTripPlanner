package org.opentripplanner.osm.tagmapping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.opentripplanner.street.model.StreetTraversalPermission.ALL;
import static org.opentripplanner.street.model.StreetTraversalPermission.CAR;
import static org.opentripplanner.street.model.StreetTraversalPermission.NONE;
import static org.opentripplanner.street.model.StreetTraversalPermission.PEDESTRIAN;
import static org.opentripplanner.street.model.StreetTraversalPermission.PEDESTRIAN_AND_BICYCLE;

import org.junit.jupiter.api.Test;
import org.opentripplanner.osm.model.OsmWay;
import org.opentripplanner.osm.wayproperty.WayPropertySet;

class HoustonMapperTest {

  static final WayPropertySet WPS = new HoustonMapper().buildWayPropertySet();

  @Test
  public void lamarTunnel() {
    // https://www.openstreetmap.org/way/127288293
    var tunnel = OsmWay.of()
      .setTag("highway", "footway")
      .setTag("indoor", "yes")
      .setTag("layer", "-1")
      .setTag("lit", "yes")
      .setTag("name", "Lamar Tunnel")
      .setTag("tunnel", "yes")
      .build();

    assertEquals(NONE, WPS.getDataForEntity(tunnel).getPermission());
  }

  @Test
  public void harrisCountyTunnel() {
    // https://www.openstreetmap.org/way/127288288
    var tunnel = OsmWay.of()
      .setTag("highway", "footway")
      .setTag("indoor", "yes")
      .setTag("name", "Harris County Tunnel")
      .setTag("tunnel", "yes")
      .build();

    assertEquals(PEDESTRIAN, WPS.getDataForEntity(tunnel).getPermission());
  }

  @Test
  public void pedestrianUnderpass() {
    // https://www.openstreetmap.org/way/783648925
    var tunnel = OsmWay.of()
      .setTag("highway", "footway")
      .setTag("layer", "-1")
      .setTag("tunnel", "yes")
      .build();

    assertEquals(PEDESTRIAN, WPS.getDataForEntity(tunnel).getPermission());
  }

  @Test
  public void cyclingTunnel() {
    // https://www.openstreetmap.org/way/220484967
    var tunnel = OsmWay.of()
      .setTag("bicycle", "designated")
      .setTag("foot", "designated")
      .setTag("highway", "cycleway")
      .setTag("segregated", "no")
      .setTag("surface", "concrete")
      .setTag("tunnel", "yes")
      .build();

    assertEquals(PEDESTRIAN_AND_BICYCLE, WPS.getDataForEntity(tunnel).getPermission());

    // https://www.openstreetmap.org/way/101884176
    tunnel = OsmWay.of()
      .setTag("highway", "cycleway")
      .setTag("layer", "-1")
      .setTag("name", "Hogg Woods Trail")
      .setTag("tunnel", "yes")
      .build();
    assertEquals(PEDESTRIAN_AND_BICYCLE, WPS.getDataForEntity(tunnel).getPermission());
  }

  @Test
  public void carTunnel() {
    // https://www.openstreetmap.org/way/598694756
    var tunnel = OsmWay.of()
      .setTag("highway", "primary")
      .setTag("hov", "lane")
      .setTag("lanes", "4")
      .setTag("layer", "-1")
      .setTag("lit", "yes")
      .setTag("maxspeed", "30 mph")
      .setTag("nam", "San Jacinto Street")
      .setTag("note:lanes", "right lane is hov")
      .setTag("oneway", "yes")
      .setTag("surface", "concrete")
      .setTag("tunnel", "yes")
      .build();

    assertEquals(ALL, WPS.getDataForWay(tunnel).forward().getPermission());
  }

  @Test
  public void carUnderpass() {
    // https://www.openstreetmap.org/way/102925214
    var tunnel = OsmWay.of()
      .setTag("highway", "motorway_link")
      .setTag("lanes", "2")
      .setTag("layer", "-1")
      .setTag("oneway", "yes")
      .setTag("tunnel", "yes")
      .build();

    assertEquals(CAR, WPS.getDataForWay(tunnel).forward().getPermission());
  }

  @Test
  public void serviceTunnel() {
    // https://www.openstreetmap.org/way/15334550
    var tunnel = OsmWay.of()
      .setTag("highway", "service")
      .setTag("layer", "-1")
      .setTag("tunnel", "yes")
      .build();

    assertEquals(ALL, WPS.getDataForEntity(tunnel).getPermission());
  }

  @Test
  public void unclassified() {
    // https://www.openstreetmap.org/way/44896136
    var tunnel = OsmWay.of()
      .setTag("highway", "unclassified")
      .setTag("name", "Ross Sterling Street")
      .setTag("layer", "-1")
      .setTag("tunnel", "yes")
      .build();

    assertEquals(ALL, WPS.getDataForEntity(tunnel).getPermission());
  }
}
