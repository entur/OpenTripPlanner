package org.opentripplanner.osm.wayproperty.specifier;

import org.opentripplanner.osm.model.OsmWay;

public class WayTestData {

  public static OsmWay carTunnel() {
    // https://www.openstreetmap.org/way/598694756
    return OsmWay.of()
      .addTag("highway", "primary")
      .addTag("hov", "lane")
      .addTag("lanes", "4")
      .addTag("layer", "-1")
      .addTag("lit", "yes")
      .addTag("maxspeed", "30 mph")
      .addTag("name", "San Jacinto Street")
      .addTag("note:lanes", "right lane is hov")
      .addTag("oneway", "yes")
      .addTag("surface", "concrete")
      .addTag("tunnel", "yes")
      .build();
  }

  public static OsmWay pedestrianTunnel() {
    // https://www.openstreetmap.org/way/127288293
    return OsmWay.of()
      .addTag("highway", "footway")
      .addTag("indoor", "yes")
      .addTag("layer", "-1")
      .addTag("lit", "yes")
      .addTag("name", "Lamar Tunnel")
      .addTag("tunnel", "yes")
      .build();
  }

  public static OsmWay streetOnBikeRoute() {
    // https://www.openstreetmap.org/way/26443041 is part of both an lcn relation
    return OsmWay.of()
      .addTag("highway", "residential")
      .addTag("lit", "yes")
      .addTag("maxspeed", "30")
      .addTag("name", "Schulstraße")
      .addTag("oneway", "no")
      .addTag("surface", "sett")
      .addTag("rcn", "yes")
      .addTag("lcn", "yes")
      .build();
  }

  public static OsmWay stairs() {
    // https://www.openstreetmap.org/way/1058669389
    return OsmWay.of()
      .addTag("handrail", "yes")
      .addTag("highway", "steps")
      .addTag("incline", "down")
      .addTag("ramp", "yes")
      .addTag("ramp:bicycle", "yes")
      .addTag("oneway", "no")
      .addTag("step_count", "38")
      .addTag("surface", "metal")
      .build();
  }

  public static OsmWay southeastLaBonitaWay() {
    // https://www.openstreetmap.org/way/5302874
    return OsmWay.of()
      .addTag("highway", "residential")
      .addTag("name", "Southeast la Bonita Way")
      .addTag("sidewalk", "both")
      .build();
  }

  public static OsmWay southwestMayoStreet() {
    //https://www.openstreetmap.org/way/425004690
    return OsmWay.of()
      .addTag("highway", "residential")
      .addTag("name", "Southwest Mayo Street")
      .addTag("maxspeed", "25 mph")
      .addTag("sidewalk", "left")
      .build();
  }

  public static OsmWay fiveLanes() {
    return OsmWay.of().addTag("highway", "primary").addTag("lanes", "5").build();
  }

  public static OsmWay threeLanes() {
    return OsmWay.of().addTag("highway", "primary").addTag("lanes", "3").build();
  }

  public static OsmWay highwayWithCycleLane() {
    return OsmWay.of().addTag("highway", "residential").addTag("cycleway", "lane").build();
  }

  public static OsmWay cyclewayLeft() {
    return OsmWay.of()
      .addTag("highway", "residential")
      .addTag("cycleway:left", "lane")
      .build();
  }

  public static OsmWay cyclewayBoth() {
    return OsmWay.of()
      .addTag("highway", "residential")
      .addTag("cycleway:both", "lane")
      .build();
  }

  public static OsmWay footway() {
    return OsmWay.of().addTag("highway", "footway").build();
  }

  public static OsmWay footwaySharedWithBicycle() {
    return OsmWay.of()
      .addTag("highway", "footway")
      .addTag("foot", "designated")
      .addTag("bicycle", "designated")
      .build();
  }

  public static OsmWay cycleway() {
    return OsmWay.of().addTag("highway", "cycleway").build();
  }

  public static OsmWay cyclewaySharedWithFoot() {
    return OsmWay.of()
      .addTag("highway", "cycleway")
      .addTag("foot", "designated")
      .addTag("bicycle", "designated")
      .build();
  }

  public static OsmWay footwaySidewalk() {
    return OsmWay.of().addTag("footway", "sidewalk").addTag("highway", "footway").build();
  }

  public static OsmWay bridleway() {
    return OsmWay.of().addTag("highway", "bridleway").build();
  }

  public static OsmWay bridlewaySharedWithFootAndBicycle() {
    return OsmWay.of()
      .addTag("highway", "bridleway")
      .addTag("foot", "designated")
      .addTag("bicycle", "designated")
      .build();
  }

  public static OsmWay pedestrianArea() {
    return OsmWay.of().addTag("area", "yes").addTag("highway", "pedestrian").build();
  }

  public static OsmWay sidewalkBoth() {
    return OsmWay.of().addTag("highway", "primary").addTag("sidewalk", "both").build();
  }

  public static OsmWay noSidewalk() {
    return OsmWay.of().addTag("highway", "residential").addTag("sidewalk", "no").build();
  }

  public static OsmWay noSidewalkHighSpeed() {
    return OsmWay.of()
      .addTag("highway", "residential")
      .addTag("sidewalk", "no")
      .addTag("maxspeed", "55 mph")
      .build();
  }

  public static OsmWay path() {
    return OsmWay.of().addTag("highway", "path").build();
  }

  public static OsmWay motorway() {
    return OsmWay.of().addTag("highway", "motorway").build();
  }

  public static OsmWay motorwayWithBicycleAllowed() {
    return OsmWay.of().addTag("highway", "motorway").addTag("bicycle", "yes").build();
  }

  public static OsmWay motorwayRamp() {
    return OsmWay.of().addTag("highway", "motorway_link").build();
  }

  public static OsmWay highwayTrunk() {
    return OsmWay.of().addTag("highway", "trunk").build();
  }

  public static OsmWay highwayTrunkWithMotorroad() {
    return OsmWay.of().addTag("highway", "trunk").addTag("motorroad", "yes").build();
  }

  public static OsmWay highwayPrimary() {
    return OsmWay.of().addTag("highway", "primary").build();
  }

  public static OsmWay highwayPrimaryWithMotorroad() {
    return highwayPrimary().copy().addTag("motorroad", "yes").build();
  }

  public static OsmWay highwayTertiary() {
    return OsmWay.of().addTag("highway", "tertiary").build();
  }

  public static OsmWay highwaySecondary() {
    return OsmWay.of().addTag("highway", "secondary").build();
  }

  public static OsmWay highwayService() {
    return OsmWay.of().addTag("highway", "service").build();
  }

  public static OsmWay highwayServiceWithSidewalk() {
    return highwayService().copy().addTag("sidewalk", "both").build();
  }

  public static OsmWay highwayPedestrian() {
    return OsmWay.of().addTag("highway", "pedestrian").build();
  }

  public static OsmWay highwayPedestrianWithSidewalk() {
    return highwayPedestrian().copy().addTag("sidewalk", "both").build();
  }

  public static OsmWay highwayTertiaryWithSidewalk() {
    return OsmWay.of().addTag("highway", "tertiary").addTag("sidewalk", "both").build();
  }

  public static OsmWay cobblestones() {
    return OsmWay.of()
      .addTag("highway", "residential")
      .addTag("surface", "cobblestones")
      .build();
  }

  public static OsmWay cyclewayLaneTrack() {
    return OsmWay.of()
      .addTag("highway", "footway")
      .addTag("cycleway", "lane")
      .addTag("cycleway:right", "track")
      .build();
  }

  public static OsmWay tramsForward() {
    // https://www.openstreetmap.org/way/108037345
    return OsmWay.of()
      .addTag("highway", "tertiary")
      .addTag("embedded_rails:forward", "tram")
      .build();
  }

  public static OsmWay veryBadSmoothness() {
    // https://www.openstreetmap.org/way/11402648
    return OsmWay.of()
      .addTag("highway", "footway")
      .addTag("surface", "sett")
      .addTag("smoothness", "very_bad")
      .build();
  }

  public static OsmWay excellentSmoothness() {
    // https://www.openstreetmap.org/way/437167371
    return OsmWay.of()
      .addTag("highway", "cycleway")
      .addTag("segregated", "no")
      .addTag("surface", "asphalt")
      .addTag("smoothness", "excellent")
      .build();
  }

  public static OsmWay zooPlatform() {
    // https://www.openstreetmap.org/way/119108622
    return OsmWay.of()
      .addTag("public_transport", "platform")
      .addTag("usage", "tourism")
      .build();
  }

  public static OsmWay indoor(String value) {
    return OsmWay.of().addTag("indoor", value).build();
  }

  public static OsmWay parkAndRide() {
    return OsmWay.of()
      .addTag("amenity", "parking")
      .addTag("park_ride", "yes")
      .addTag("capacity", "10")
      .build();
  }

  public static OsmWay platform() {
    return OsmWay.of().addTag("public_transport", "platform").addTag("ref", "123").build();
  }
}
