package org.opentripplanner.service.vehiclerental.street;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opentripplanner.core.model.id.FeedScopedIdFactory.id;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.opentripplanner.service.vehiclerental.model.GeofencingZone;
import org.opentripplanner.street.geometry.Polygons;

class GeofencingZoneIndexTest {

  final GeofencingZone frognerPark = new GeofencingZone(
    id("frogner-park"),
    null,
    Polygons.OSLO_FROGNER_PARK,
    true,
    false
  );

  final GeofencingZone oslo = new GeofencingZone(id("oslo"), null, Polygons.OSLO, false, false);

  final GeofencingZoneIndex index = new GeofencingZoneIndex(List.of(frognerPark, oslo));

  @Test
  void pointInsideSingleZone() {
    // inside Frogner Park and inside Oslo
    var coord = new Coordinate(10.699322, 59.928667);
    var zones = index.getZonesContaining(coord);
    assertTrue(zones.contains(frognerPark));
    assertTrue(zones.contains(oslo));
  }

  @Test
  void pointInsideOnlyOuterZone() {
    // inside Oslo but outside Frogner Park
    var coord = new Coordinate(10.76411762080707, 59.95961972533365);
    var zones = index.getZonesContaining(coord);
    assertTrue(zones.contains(oslo));
    assertEquals(Set.of(oslo), zones);
  }

  @Test
  void pointOutsideAllZones() {
    // way outside Oslo
    var coord = new Coordinate(5.0, 60.5);
    var zones = index.getZonesContaining(coord);
    assertTrue(zones.isEmpty());
  }

  @Test
  void emptyIndex() {
    var emptyIndex = new GeofencingZoneIndex(List.of());
    var zones = emptyIndex.getZonesContaining(new Coordinate(10.7, 59.9));
    assertTrue(zones.isEmpty());
  }
}
