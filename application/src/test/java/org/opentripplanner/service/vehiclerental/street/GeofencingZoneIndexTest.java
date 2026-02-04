package org.opentripplanner.service.vehiclerental.street;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opentripplanner.transit.model._data.TimetableRepositoryForTest.id;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.opentripplanner._support.geometry.Polygons;
import org.opentripplanner.service.vehiclerental.model.GeofencingZone;

class GeofencingZoneIndexTest {

  // Points inside Frogner Park
  static final Coordinate INSIDE_FROGNER_1 = new Coordinate(10.699322, 59.928667);
  static final Coordinate INSIDE_FROGNER_2 = new Coordinate(10.703902, 59.9245634);

  // Point outside Frogner Park but inside Oslo
  static final Coordinate OUTSIDE_FROGNER_IN_OSLO = new Coordinate(10.70637639, 59.921212);

  // Point inside Oslo business area
  static final Coordinate INSIDE_OSLO = new Coordinate(10.76411762080707, 59.95961972533365);

  // Point outside Oslo
  static final Coordinate OUTSIDE_OSLO = new Coordinate(10.5, 60.5);

  // drop-off banned, traversal allowed
  final GeofencingZone frognerParkZone = new GeofencingZone(
    id("frogner-park"),
    null,
    Polygons.OSLO_FROGNER_PARK,
    true,
    false
  );

  // drop-off banned, traversal banned
  final GeofencingZone frognerParkNoTraversal = new GeofencingZone(
    id("frogner-park-no-traversal"),
    null,
    Polygons.OSLO_FROGNER_PARK,
    true,
    true
  );

  // business area: drop-off allowed, traversal allowed
  final GeofencingZone osloBusinessArea = new GeofencingZone(
    id("oslo"),
    null,
    Polygons.OSLO,
    false,
    false
  );

  @Test
  void emptyIndex() {
    var index = new GeofencingZoneIndex(List.of());
    assertTrue(index.isEmpty());
    assertEquals(0, index.size());
    assertTrue(index.getZonesContaining(INSIDE_FROGNER_1).isEmpty());
  }

  @Test
  void singleZone() {
    var index = new GeofencingZoneIndex(List.of(frognerParkZone));

    assertFalse(index.isEmpty());
    assertEquals(1, index.size());

    // Point inside zone
    var zones = index.getZonesContaining(INSIDE_FROGNER_1);
    assertEquals(1, zones.size());
    assertTrue(zones.contains(frognerParkZone));

    // Point outside zone
    assertTrue(index.getZonesContaining(OUTSIDE_FROGNER_IN_OSLO).isEmpty());
  }

  @Test
  void multipleNonOverlappingZones() {
    var index = new GeofencingZoneIndex(List.of(frognerParkZone, osloBusinessArea));

    assertEquals(2, index.size());

    // Point inside Frogner Park (also inside Oslo)
    var zonesAtFrogner = index.getZonesContaining(INSIDE_FROGNER_1);
    assertEquals(2, zonesAtFrogner.size());
    assertTrue(zonesAtFrogner.contains(frognerParkZone));
    assertTrue(zonesAtFrogner.contains(osloBusinessArea));

    // Point outside Frogner but inside Oslo
    var zonesOutsideFrogner = index.getZonesContaining(OUTSIDE_FROGNER_IN_OSLO);
    assertEquals(1, zonesOutsideFrogner.size());
    assertTrue(zonesOutsideFrogner.contains(osloBusinessArea));

    // Point outside both
    assertTrue(index.getZonesContaining(OUTSIDE_OSLO).isEmpty());
  }

  @Test
  void overlappingZones() {
    // Two zones covering the same area
    var index = new GeofencingZoneIndex(List.of(frognerParkZone, frognerParkNoTraversal));

    assertEquals(2, index.size());

    // Point inside both overlapping zones
    var zones = index.getZonesContaining(INSIDE_FROGNER_1);
    assertEquals(2, zones.size());
    assertTrue(zones.contains(frognerParkZone));
    assertTrue(zones.contains(frognerParkNoTraversal));
  }

  @Test
  void getRestrictedZonesOnly() {
    var index = new GeofencingZoneIndex(List.of(frognerParkZone, osloBusinessArea));

    // Point inside both zones, but only Frogner Park has restrictions
    var restrictedZones = index.getRestrictedZonesContaining(INSIDE_FROGNER_1);
    assertEquals(1, restrictedZones.size());
    assertTrue(restrictedZones.contains(frognerParkZone));

    // Point outside Frogner, inside Oslo - no restricted zones
    var restrictedOutside = index.getRestrictedZonesContaining(OUTSIDE_FROGNER_IN_OSLO);
    assertTrue(restrictedOutside.isEmpty());
  }

  @Test
  void getZonesForNetwork() {
    var index = new GeofencingZoneIndex(List.of(frognerParkZone, osloBusinessArea));

    // Get zones for specific network (both use feed "F" from TimetableRepositoryForTest.id())
    var zonesForNetwork = index.getZonesContaining(INSIDE_FROGNER_1, "F");
    assertEquals(2, zonesForNetwork.size());

    // Non-existent network
    var zonesForUnknown = index.getZonesContaining(INSIDE_FROGNER_1, "unknown-network");
    assertTrue(zonesForUnknown.isEmpty());
  }
}
