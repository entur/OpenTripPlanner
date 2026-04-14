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
        .addTag("bicycle", "yes")
        .addTag("foot", "designated")
        .addTag("footway", "sidewalk")
        .addTag("highway", "footway")
        .addTag("lit", "yes")
        .addTag("oneway", "no")
        .addTag("traffic_sign", "DE:239,1022-10")
        .build();
      assertEquals(1.2, WPS.getDataForWay(way).forward().bicycleSafety(), epsilon);
    }

    @Test
    void cyclewayOpposite() {
      var way = OsmWay.of()
        .addTag("cycleway", "opposite")
        .addTag("highway", "residential")
        .addTag("lit", "yes")
        .addTag("maxspeed", "30")
        .addTag("name", "Freibadstraße")
        .addTag("oneway", "yes")
        .addTag("oneway:bicycle", "no")
        .addTag("parking:lane:left", "parallel")
        .addTag("parking:lane:right", "no_parking")
        .addTag("sidewalk", "both")
        .addTag("source:maxspeed", "DE:zone:30")
        .addTag("surface", "asphalt")
        .addTag("width", "6.5")
        .addTag("zone:traffic", "DE:urban")
        .build();
      assertEquals(0.9, WPS.getDataForWay(way).forward().bicycleSafety(), epsilon);
      // walk safety should be default
      assertEquals(1, WPS.getDataForWay(way).forward().walkSafety(), epsilon);
    }

    @Test
    void bikePath() {
      // way332589799 (Radschnellweg BW1)
      var way = OsmWay.of()
        .addTag("bicycle", "designated")
        .addTag("class:bicycle", "2")
        .addTag("class:bicycle:roadcycling", "1")
        .addTag("highway", "track")
        .addTag("horse", "forestry")
        .addTag("lcn", "yes")
        .addTag("lit", "yes")
        .addTag("maxspeed", "30")
        .addTag("motor_vehicle", "forestry")
        .addTag("name", "Römerstraße")
        .addTag("smoothness", "excellent")
        .addTag("source:maxspeed", "sign")
        .addTag("surface", "asphalt")
        .addTag("tracktype", "grade1")
        .build();
      assertEquals(0.693, WPS.getDataForWay(way).forward().bicycleSafety(), epsilon);
    }

    @Test
    void track() {
      var way = OsmWay.of()
        .addTag("highway", "track")
        .addTag("motor_vehicle", "agricultural")
        .addTag("surface", "asphalt")
        .addTag("tracktype", "grade1")
        .addTag("traffic_sign", "DE:260,1026-36")
        .addTag("width", "2.5")
        .build();
      assertEquals(1.0, WPS.getDataForWay(way).forward().bicycleSafety(), epsilon);
    }
  }

  @Test
  void testPermissions() {
    // https://www.openstreetmap.org/way/124263424
    var way = OsmWay.of().addTag("highway", "track").addTag("tracktype", "grade1").build();
    assertEquals(
      StreetTraversalPermission.PEDESTRIAN_AND_BICYCLE,
      WPS.getDataForEntity(way).getPermission()
    );

    // https://www.openstreetmap.org/way/5155805
    way = OsmWay.of()
      .addTag("access:lanes:forward", "yes|no")
      .addTag("bicycle:lanes:forward", "|designated")
      .addTag("change:lanes:forward", "not_right|no")
      .addTag("cycleway:right", "lane")
      .addTag("cycleway:right:lane", "exclusive")
      .addTag("cycleway:right:traffic_sign", "DE:237")
      .addTag("highway", "unclassified")
      .addTag("lanes", "3")
      .addTag("lanes:backward", "2")
      .addTag("lanes:forward", "1")
      .addTag("lit", "yes")
      .addTag("maxspeed", "50")
      .addTag("name", "Krailenshaldenstraße")
      .addTag("parking:lane:both", "no_stopping")
      .addTag("sidewalk", "left")
      .addTag("smoothness", "good")
      .addTag("source:maxspeed", "DE:urban")
      .addTag("surface", "asphalt")
      .addTag("turn:lanes:backward", "left|through;right")
      .addTag("width:lanes:forward", "|1.4")
      .addTag("zone:traffic", "DE:urban")
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
        .addTag("highway", "residential")
        .addTag("rcn", "yes")
        .addTag("lcn", "yes")
        .build();

      var justLcn = OsmWay.of().addTag("lcn", "yes").addTag("highway", "residential").build();

      var residential = OsmWay.of().addTag("highway", "residential").build();

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
        .addTag("highway", "residential")
        .addTag("bicycle_road", "yes")
        .addTag("cyclestreet", "yes")
        .addTag("lcn", "yes")
        .build();

      var justBicycleRoad = OsmWay.of()
        .addTag("bicycle_road", "yes")
        .addTag("highway", "residential")
        .build();

      var justCyclestreet = OsmWay.of()
        .addTag("cyclestreet", "yes")
        .addTag("highway", "residential")
        .build();

      var justLcn = OsmWay.of().addTag("lcn", "yes").addTag("highway", "residential").build();

      var residential = OsmWay.of().addTag("highway", "residential").build();

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
      .addTag("highway", "residential")
      .addTag("junction", "roundabout")
      .build();
    assertEquals(ALL, WPS.getDataForWay(residential).forward().getPermission());
    assertEquals(PEDESTRIAN, WPS.getDataForWay(residential).backward().getPermission());

    //https://www.openstreetmap.org/way/31109939
    var primary = OsmWay.of()
      .addTag("highway", "primary")
      .addTag("junction", "roundabout")
      .build();
    assertEquals(BICYCLE_AND_CAR, WPS.getDataForWay(primary).forward().getPermission());
    assertEquals(NONE, WPS.getDataForWay(primary).backward().getPermission());
  }

  @Test
  void setCorrectBikeSafetyValuesForBothDirections() {
    // https://www.openstreetmap.org/way/13420871
    var residential = OsmWay.of()
      .addTag("highway", "residential")
      .addTag("lit", "yes")
      .addTag("maxspeed", "30")
      .addTag("name", "Auf der Heide")
      .addTag("surface", "asphalt")
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
    var steps = OsmWay.of().addTag("highway", "steps").build();
    assertEquals(StreetTraversalPermission.PEDESTRIAN, WPS.getDataForEntity(steps).getPermission());
  }

  @Test
  void testGermanAutobahnSpeed() {
    // https://www.openstreetmap.org/way/10879847
    var alzentalstr = OsmWay.of()
      .addTag("highway", "residential")
      .addTag("lit", "yes")
      .addTag("maxspeed", "30")
      .addTag("name", "Alzentalstraße")
      .addTag("surface", "asphalt")
      .build();
    assertEquals(
      8.33333969116211,
      WPS.getCarSpeedForWay(alzentalstr, TraverseDirection.FORWARD),
      epsilon
    );

    var autobahn = OsmWay.of()
      .addTag("highway", "motorway")
      .addTag("maxspeed", "none")
      .build();
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
    var way = OsmWay.of().addTag("public_transport", "platform").addTag("area", "yes").build();
    assertEquals(PEDESTRIAN, WPS.getDataForEntity(way).getPermission());
    way = way.copy().addTag("bicycle", "yes").build();
    assertEquals(PEDESTRIAN_AND_BICYCLE, WPS.getDataForEntity(way).getPermission());
  }
}
