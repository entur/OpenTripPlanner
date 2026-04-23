package org.opentripplanner.osm.wayproperty.specifier;

import org.opentripplanner.osm.model.OsmWay;

public class WayTestData {

  public static OsmWay carTunnel() {
    // https://www.openstreetmap.org/way/598694756
    return OsmWay.of()
      .setTag("highway", "primary")
      .setTag("hov", "lane")
      .setTag("lanes", "4")
      .setTag("layer", "-1")
      .setTag("lit", "yes")
      .setTag("maxspeed", "30 mph")
      .setTag("name", "San Jacinto Street")
      .setTag("note:lanes", "right lane is hov")
      .setTag("oneway", "yes")
      .setTag("surface", "concrete")
      .setTag("tunnel", "yes")
      .build();
  }

  public static OsmWay pedestrianTunnel() {
    // https://www.openstreetmap.org/way/127288293
    return OsmWay.of()
      .setTag("highway", "footway")
      .setTag("indoor", "yes")
      .setTag("layer", "-1")
      .setTag("lit", "yes")
      .setTag("name", "Lamar Tunnel")
      .setTag("tunnel", "yes")
      .build();
  }

  public static OsmWay streetOnBikeRoute() {
    // https://www.openstreetmap.org/way/26443041 is part of both an lcn relation
    return OsmWay.of()
      .setTag("highway", "residential")
      .setTag("lit", "yes")
      .setTag("maxspeed", "30")
      .setTag("name", "Schulstraße")
      .setTag("oneway", "no")
      .setTag("surface", "sett")
      .setTag("rcn", "yes")
      .setTag("lcn", "yes")
      .build();
  }

  public static OsmWay stairs() {
    // https://www.openstreetmap.org/way/1058669389
    return OsmWay.of()
      .setTag("handrail", "yes")
      .setTag("highway", "steps")
      .setTag("incline", "down")
      .setTag("ramp", "yes")
      .setTag("ramp:bicycle", "yes")
      .setTag("oneway", "no")
      .setTag("step_count", "38")
      .setTag("surface", "metal")
      .build();
  }

  public static OsmWay southeastLaBonitaWay() {
    // https://www.openstreetmap.org/way/5302874
    return OsmWay.of()
      .setTag("highway", "residential")
      .setTag("name", "Southeast la Bonita Way")
      .setTag("sidewalk", "both")
      .build();
  }

  public static OsmWay southwestMayoStreet() {
    //https://www.openstreetmap.org/way/425004690
    return OsmWay.of()
      .setTag("highway", "residential")
      .setTag("name", "Southwest Mayo Street")
      .setTag("maxspeed", "25 mph")
      .setTag("sidewalk", "left")
      .build();
  }

  public static OsmWay fiveLanes() {
    return OsmWay.of().setTag("highway", "primary").setTag("lanes", "5").build();
  }

  public static OsmWay threeLanes() {
    return OsmWay.of().setTag("highway", "primary").setTag("lanes", "3").build();
  }

  public static OsmWay highwayWithCycleLane() {
    return OsmWay.of().setTag("highway", "residential").setTag("cycleway", "lane").build();
  }

  public static OsmWay cyclewayLeft() {
    return OsmWay.of().setTag("highway", "residential").setTag("cycleway:left", "lane").build();
  }

  public static OsmWay cyclewayBoth() {
    return OsmWay.of().setTag("highway", "residential").setTag("cycleway:both", "lane").build();
  }

  public static OsmWay footway() {
    return OsmWay.of().setTag("highway", "footway").build();
  }

  public static OsmWay footwaySharedWithBicycle() {
    return OsmWay.of()
      .setTag("highway", "footway")
      .setTag("foot", "designated")
      .setTag("bicycle", "designated")
      .build();
  }

  public static OsmWay cycleway() {
    return OsmWay.of().setTag("highway", "cycleway").build();
  }

  public static OsmWay cyclewaySharedWithFoot() {
    return OsmWay.of()
      .setTag("highway", "cycleway")
      .setTag("foot", "designated")
      .setTag("bicycle", "designated")
      .build();
  }

  public static OsmWay footwaySidewalk() {
    return OsmWay.of().setTag("footway", "sidewalk").setTag("highway", "footway").build();
  }

  public static OsmWay bridleway() {
    return OsmWay.of().setTag("highway", "bridleway").build();
  }

  public static OsmWay bridlewaySharedWithFootAndBicycle() {
    return OsmWay.of()
      .setTag("highway", "bridleway")
      .setTag("foot", "designated")
      .setTag("bicycle", "designated")
      .build();
  }

  public static OsmWay pedestrianArea() {
    return OsmWay.of().setTag("area", "yes").setTag("highway", "pedestrian").build();
  }

  public static OsmWay sidewalkBoth() {
    return OsmWay.of().setTag("highway", "primary").setTag("sidewalk", "both").build();
  }

  public static OsmWay noSidewalk() {
    return OsmWay.of().setTag("highway", "residential").setTag("sidewalk", "no").build();
  }

  public static OsmWay noSidewalkHighSpeed() {
    return OsmWay.of()
      .setTag("highway", "residential")
      .setTag("sidewalk", "no")
      .setTag("maxspeed", "55 mph")
      .build();
  }

  public static OsmWay path() {
    return OsmWay.of().setTag("highway", "path").build();
  }

  public static OsmWay motorway() {
    return OsmWay.of().setTag("highway", "motorway").build();
  }

  public static OsmWay motorwayWithBicycleAllowed() {
    return OsmWay.of().setTag("highway", "motorway").setTag("bicycle", "yes").build();
  }

  public static OsmWay motorwayRamp() {
    return OsmWay.of().setTag("highway", "motorway_link").build();
  }

  public static OsmWay highwayTrunk() {
    return OsmWay.of().setTag("highway", "trunk").build();
  }

  public static OsmWay highwayTrunkWithMotorroad() {
    return OsmWay.of().setTag("highway", "trunk").setTag("motorroad", "yes").build();
  }

  public static OsmWay highwayPrimary() {
    return OsmWay.of().setTag("highway", "primary").build();
  }

  public static OsmWay highwayPrimaryWithMotorroad() {
    return highwayPrimary().copy().setTag("motorroad", "yes").build();
  }

  public static OsmWay highwayTertiary() {
    return OsmWay.of().setTag("highway", "tertiary").build();
  }

  public static OsmWay highwaySecondary() {
    return OsmWay.of().setTag("highway", "secondary").build();
  }

  public static OsmWay highwayService() {
    return OsmWay.of().setTag("highway", "service").build();
  }

  public static OsmWay highwayServiceWithSidewalk() {
    return highwayService().copy().setTag("sidewalk", "both").build();
  }

  public static OsmWay highwayPedestrian() {
    return OsmWay.of().setTag("highway", "pedestrian").build();
  }

  public static OsmWay highwayPedestrianWithSidewalk() {
    return highwayPedestrian().copy().setTag("sidewalk", "both").build();
  }

  public static OsmWay highwayTertiaryWithSidewalk() {
    return OsmWay.of().setTag("highway", "tertiary").setTag("sidewalk", "both").build();
  }

  public static OsmWay cobblestones() {
    return OsmWay.of().setTag("highway", "residential").setTag("surface", "cobblestones").build();
  }

  public static OsmWay cyclewayLaneTrack() {
    return OsmWay.of()
      .setTag("highway", "footway")
      .setTag("cycleway", "lane")
      .setTag("cycleway:right", "track")
      .build();
  }

  public static OsmWay tramsForward() {
    // https://www.openstreetmap.org/way/108037345
    return OsmWay.of()
      .setTag("highway", "tertiary")
      .setTag("embedded_rails:forward", "tram")
      .build();
  }

  public static OsmWay veryBadSmoothness() {
    // https://www.openstreetmap.org/way/11402648
    return OsmWay.of()
      .setTag("highway", "footway")
      .setTag("surface", "sett")
      .setTag("smoothness", "very_bad")
      .build();
  }

  public static OsmWay excellentSmoothness() {
    // https://www.openstreetmap.org/way/437167371
    return OsmWay.of()
      .setTag("highway", "cycleway")
      .setTag("segregated", "no")
      .setTag("surface", "asphalt")
      .setTag("smoothness", "excellent")
      .build();
  }

  public static OsmWay zooPlatform() {
    // https://www.openstreetmap.org/way/119108622
    return OsmWay.of().setTag("public_transport", "platform").setTag("usage", "tourism").build();
  }

  public static OsmWay indoor(String value) {
    return OsmWay.of().setTag("indoor", value).build();
  }

  public static OsmWay parkAndRide() {
    return OsmWay.of()
      .setTag("amenity", "parking")
      .setTag("park_ride", "yes")
      .setTag("capacity", "10")
      .build();
  }

  public static OsmWay platform() {
    return OsmWay.of().setTag("public_transport", "platform").setTag("ref", "123").build();
  }
}
