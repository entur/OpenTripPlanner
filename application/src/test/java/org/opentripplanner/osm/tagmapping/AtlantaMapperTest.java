package org.opentripplanner.osm.tagmapping;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.opentripplanner.osm.model.OsmWay;
import org.opentripplanner.osm.wayproperty.WayPropertySet;
import org.opentripplanner.street.model.StreetTraversalPermission;

class AtlantaMapperTest {

  private static final WayPropertySet WPS = new AtlantaMapper().buildWayPropertySet();

  // Most OSM trunk roads in Atlanta are (large) city roads that are permitted for all modes.
  // (The default TagMapper implementation is car-only.)
  // TODO: Handle exceptions such as:
  //  - Northside Drive between Marietta Street and Tech Parkway (northbound)
  //    (https://www.openstreetmap.org/way/96395009, no sidewalk, but possible to bike)
  //  - Portions of Freedom Parkway that are freeway/motorway-like (https://www.openstreetmap.org/way/88171817)

  @Test
  public void peachtreeRoad() {
    // Peachtree Rd in Atlanta has sidewalks, and bikes are allowed.
    // https://www.openstreetmap.org/way/144429544
    OsmWay peachtreeRd = OsmWay.of()
      .addTag("highway", "trunk")
      .addTag("lanes", "6")
      .addTag("name", "Peachtree Road")
      .addTag("ref", "US 19;GA 9")
      .addTag("surface", "asphalt")
      .addTag("tiger:county", "Fulton, GA")
      .build();

    assertEquals(StreetTraversalPermission.ALL, WPS.getDataForEntity(peachtreeRd).getPermission());
  }

  @Test
  public void deKalbAvenue() {
    // "Outer" ramps from DeKalb Ave onto Moreland Ave in Atlanta have sidewalks, and bikes are allowed.
    // https://www.openstreetmap.org/way/9164434
    OsmWay morelandRamp = OsmWay.of()
      .addTag("highway", "trunk_link")
      .addTag("lanes", "1")
      .addTag("oneway", "yes")
      .addTag("tiger:cfcc", "A63")
      .addTag("tiger:county", "DeKalb, GA")
      .addTag("tiger:reviewed", "no")
      .build();

    assertEquals(
      StreetTraversalPermission.ALL,
      WPS.getDataForWay(morelandRamp).forward().getPermission()
    );
  }

  @Test
  public void tenthStreetNE() {
    // For sanity check, secondary roads (e.g. 10th Street) should remain allowed for all modes.
    // https://www.openstreetmap.org/way/505912700
    OsmWay tenthSt = OsmWay.of()
      .addTag("highway", "secondary")
      .addTag("lanes", "4")
      .addTag("maxspeed", "30 mph")
      .addTag("name", "10th Street Northeast")
      .addTag("oneway", "no")
      .addTag("source:maxspeed", "sign")
      .addTag("surface", "asphalt")
      .addTag("tiger:cfcc", "A41")
      .addTag("tiger:county", "Fulton, GA")
      .addTag("tiger:reviewed", "no")
      .build();
    // Some other params omitted.
    assertEquals(StreetTraversalPermission.ALL, WPS.getDataForEntity(tenthSt).getPermission());
  }
}
