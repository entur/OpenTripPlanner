package org.opentripplanner.osm.tagmapping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.opentripplanner.street.model.StreetTraversalPermission.ALL;
import static org.opentripplanner.street.model.StreetTraversalPermission.BICYCLE_AND_CAR;
import static org.opentripplanner.street.model.StreetTraversalPermission.NONE;
import static org.opentripplanner.street.model.StreetTraversalPermission.PEDESTRIAN;
import static org.opentripplanner.street.model.StreetTraversalPermission.PEDESTRIAN_AND_BICYCLE;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.opentripplanner.osm.model.OsmWay;
import org.opentripplanner.osm.model.TraverseDirection;
import org.opentripplanner.osm.wayproperty.WayPropertySet;
import org.opentripplanner.street.model.StreetTraversalPermission;

public class GermanyMapperTest {

  static final WayPropertySet WPS = new GermanyMapper().buildWayPropertySet();
  static float epsilon = 0.01f;

  /**
   * Test that bike safety factors are calculated accurately
   */
  @Nested
  class BikeSafety {

    @Test
    void testBikeSafety() {
      // way 361961158
      var way = OsmWay.of()
        .setTag("bicycle", "yes")
        .setTag("foot", "designated")
        .setTag("footway", "sidewalk")
        .setTag("highway", "footway")
        .setTag("lit", "yes")
        .setTag("oneway", "no")
        .setTag("traffic_sign", "DE:239,1022-10")
        .build();
      assertEquals(1.2, WPS.getDataForWay(way).forward().bicycleSafety(), epsilon);
    }

    @Test
    void cyclewayOpposite() {
      var way = OsmWay.of()
        .setTag("cycleway", "opposite")
        .setTag("highway", "residential")
        .setTag("lit", "yes")
        .setTag("maxspeed", "30")
        .setTag("name", "Freibadstraße")
        .setTag("oneway", "yes")
        .setTag("oneway:bicycle", "no")
        .setTag("parking:lane:left", "parallel")
        .setTag("parking:lane:right", "no_parking")
        .setTag("sidewalk", "both")
        .setTag("source:maxspeed", "DE:zone:30")
        .setTag("surface", "asphalt")
        .setTag("width", "6.5")
        .setTag("zone:traffic", "DE:urban")
        .build();
      assertEquals(0.9, WPS.getDataForWay(way).forward().bicycleSafety(), epsilon);
      // walk safety should be default
      assertEquals(1, WPS.getDataForWay(way).forward().walkSafety(), epsilon);
    }

    @Test
    void bikePath() {
      // way332589799 (Radschnellweg BW1)
      var way = OsmWay.of()
        .setTag("bicycle", "designated")
        .setTag("class:bicycle", "2")
        .setTag("class:bicycle:roadcycling", "1")
        .setTag("highway", "track")
        .setTag("horse", "forestry")
        .setTag("lcn", "yes")
        .setTag("lit", "yes")
        .setTag("maxspeed", "30")
        .setTag("motor_vehicle", "forestry")
        .setTag("name", "Römerstraße")
        .setTag("smoothness", "excellent")
        .setTag("source:maxspeed", "sign")
        .setTag("surface", "asphalt")
        .setTag("tracktype", "grade1")
        .build();
      assertEquals(0.693, WPS.getDataForWay(way).forward().bicycleSafety(), epsilon);
    }

    @Test
    void track() {
      var way = OsmWay.of()
        .setTag("highway", "track")
        .setTag("motor_vehicle", "agricultural")
        .setTag("surface", "asphalt")
        .setTag("tracktype", "grade1")
        .setTag("traffic_sign", "DE:260,1026-36")
        .setTag("width", "2.5")
        .build();
      assertEquals(1.0, WPS.getDataForWay(way).forward().bicycleSafety(), epsilon);
    }
  }

  @Test
  void testPermissions() {
    // https://www.openstreetmap.org/way/124263424
    var way = OsmWay.of().setTag("highway", "track").setTag("tracktype", "grade1").build();
    assertEquals(
      StreetTraversalPermission.PEDESTRIAN_AND_BICYCLE,
      WPS.getDataForEntity(way).getPermission()
    );

    // https://www.openstreetmap.org/way/5155805
    way = OsmWay.of()
      .setTag("access:lanes:forward", "yes|no")
      .setTag("bicycle:lanes:forward", "|designated")
      .setTag("change:lanes:forward", "not_right|no")
      .setTag("cycleway:right", "lane")
      .setTag("cycleway:right:lane", "exclusive")
      .setTag("cycleway:right:traffic_sign", "DE:237")
      .setTag("highway", "unclassified")
      .setTag("lanes", "3")
      .setTag("lanes:backward", "2")
      .setTag("lanes:forward", "1")
      .setTag("lit", "yes")
      .setTag("maxspeed", "50")
      .setTag("name", "Krailenshaldenstraße")
      .setTag("parking:lane:both", "no_stopping")
      .setTag("sidewalk", "left")
      .setTag("smoothness", "good")
      .setTag("source:maxspeed", "DE:urban")
      .setTag("surface", "asphalt")
      .setTag("turn:lanes:backward", "left|through;right")
      .setTag("width:lanes:forward", "|1.4")
      .setTag("zone:traffic", "DE:urban")
      .build();

    assertEquals(ALL, WPS.getDataForEntity(way).getPermission());
  }

  @Nested
  class BikeRouteNetworks {

    @Test
    void lcnAndRcnShouldNotBeAddedUp() {
      // https://www.openstreetmap.org/way/26443041 is part of both an lcn and rcn but that shouldn't mean that
      // it is to be more heavily favoured than other ways that are part of just one.

      var both = OsmWay.of()
        .setTag("highway", "residential")
        .setTag("rcn", "yes")
        .setTag("lcn", "yes")
        .build();

      var justLcn = OsmWay.of().setTag("lcn", "yes").setTag("highway", "residential").build();

      var residential = OsmWay.of().setTag("highway", "residential").build();

      assertEquals(
        WPS.getDataForWay(both).forward().bicycleSafety(),
        WPS.getDataForWay(justLcn).forward().bicycleSafety(),
        epsilon
      );

      assertEquals(0.6859, WPS.getDataForWay(both).forward().bicycleSafety(), epsilon);

      assertEquals(0.98, WPS.getDataForWay(residential).forward().bicycleSafety(), epsilon);
    }

    @Test
    void bicycleRoadAndLcnShouldNotBeAddedUp() {
      // https://www.openstreetmap.org/way/22201321 was tagged as bicycle_road without lcn
      // make it so all ways tagged as some kind of cyclestreets are considered as equally safe

      var both = OsmWay.of()
        .setTag("highway", "residential")
        .setTag("bicycle_road", "yes")
        .setTag("cyclestreet", "yes")
        .setTag("lcn", "yes")
        .build();

      var justBicycleRoad = OsmWay.of()
        .setTag("bicycle_road", "yes")
        .setTag("highway", "residential")
        .build();

      var justCyclestreet = OsmWay.of()
        .setTag("cyclestreet", "yes")
        .setTag("highway", "residential")
        .build();

      var justLcn = OsmWay.of().setTag("lcn", "yes").setTag("highway", "residential").build();

      var residential = OsmWay.of().setTag("highway", "residential").build();

      assertEquals(
        WPS.getDataForWay(justCyclestreet).forward().bicycleSafety(),
        WPS.getDataForWay(justLcn).forward().bicycleSafety(),
        epsilon
      );

      assertEquals(
        WPS.getDataForWay(both).forward().bicycleSafety(),
        WPS.getDataForWay(justBicycleRoad).forward().bicycleSafety(),
        epsilon
      );

      assertEquals(
        WPS.getDataForWay(both).forward().bicycleSafety(),
        WPS.getDataForWay(justCyclestreet).forward().bicycleSafety(),
        epsilon
      );

      assertEquals(
        WPS.getDataForWay(both).forward().bicycleSafety(),
        WPS.getDataForWay(justLcn).forward().bicycleSafety(),
        epsilon
      );

      assertEquals(0.6859, WPS.getDataForWay(both).forward().bicycleSafety(), epsilon);

      assertEquals(0.98, WPS.getDataForWay(residential).forward().bicycleSafety(), epsilon);
    }
  }

  @Test
  void setCorrectPermissionsForRoundabouts() {
    // https://www.openstreetmap.org/way/184185551
    var residential = OsmWay.of()
      .setTag("highway", "residential")
      .setTag("junction", "roundabout")
      .build();
    assertEquals(ALL, WPS.getDataForWay(residential).forward().getPermission());
    assertEquals(PEDESTRIAN, WPS.getDataForWay(residential).backward().getPermission());

    //https://www.openstreetmap.org/way/31109939
    var primary = OsmWay.of().setTag("highway", "primary").setTag("junction", "roundabout").build();
    assertEquals(BICYCLE_AND_CAR, WPS.getDataForWay(primary).forward().getPermission());
    assertEquals(NONE, WPS.getDataForWay(primary).backward().getPermission());
  }

  @Test
  void setCorrectBikeSafetyValuesForBothDirections() {
    // https://www.openstreetmap.org/way/13420871
    var residential = OsmWay.of()
      .setTag("highway", "residential")
      .setTag("lit", "yes")
      .setTag("maxspeed", "30")
      .setTag("name", "Auf der Heide")
      .setTag("surface", "asphalt")
      .build();
    assertEquals(
      WPS.getDataForWay(residential).forward().bicycleSafety(),
      WPS.getDataForWay(residential).backward().bicycleSafety(),
      epsilon
    );
  }

  @Test
  void setCorrectPermissionsForSteps() {
    // https://www.openstreetmap.org/way/64359102
    var steps = OsmWay.of().setTag("highway", "steps").build();
    assertEquals(StreetTraversalPermission.PEDESTRIAN, WPS.getDataForEntity(steps).getPermission());
  }

  @Test
  void testGermanAutobahnSpeed() {
    // https://www.openstreetmap.org/way/10879847
    var alzentalstr = OsmWay.of()
      .setTag("highway", "residential")
      .setTag("lit", "yes")
      .setTag("maxspeed", "30")
      .setTag("name", "Alzentalstraße")
      .setTag("surface", "asphalt")
      .build();
    assertEquals(
      8.33333969116211,
      WPS.getCarSpeedForWay(alzentalstr, TraverseDirection.FORWARD),
      epsilon
    );

    var autobahn = OsmWay.of().setTag("highway", "motorway").setTag("maxspeed", "none").build();
    assertEquals(
      33.33000183105469,
      WPS.getCarSpeedForWay(autobahn, TraverseDirection.FORWARD),
      epsilon
    );
  }

  /**
   * Test that biking is not allowed in transit platforms
   */
  @Test
  public void testArea() {
    var way = OsmWay.of().setTag("public_transport", "platform").setTag("area", "yes").build();
    assertEquals(PEDESTRIAN, WPS.getDataForEntity(way).getPermission());
    way = way.copy().setTag("bicycle", "yes").build();
    assertEquals(PEDESTRIAN_AND_BICYCLE, WPS.getDataForEntity(way).getPermission());
  }
}
