package org.opentripplanner.service.vehiclerental.street;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opentripplanner.core.model.id.FeedScopedIdFactory.id;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.opentripplanner.service.vehiclerental.model.GeofencingZone;
import org.opentripplanner.street.geometry.Polygons;
import org.opentripplanner.street.model.RentalRestrictionExtension;

class GeofencingBoundaryExtensionTest {

  final GeofencingZone zone = new GeofencingZone(
    id("frogner-park"),
    null,
    Polygons.OSLO_FROGNER_PARK,
    true,
    false
  );

  final GeofencingBoundaryExtension entering = new GeofencingBoundaryExtension(zone, true);
  final GeofencingBoundaryExtension exiting = new GeofencingBoundaryExtension(zone, false);

  @Test
  void traversalBannedReturnsFalse() {
    assertFalse(entering.traversalBanned(null));
    assertFalse(exiting.traversalBanned(null));
  }

  @Test
  void dropOffBannedReturnsFalse() {
    assertFalse(entering.dropOffBanned(null));
    assertFalse(exiting.dropOffBanned(null));
  }

  @Test
  void enteringFlag() {
    assertTrue(entering.entering());
    assertFalse(exiting.entering());
  }

  @Test
  void zoneAccessor() {
    assertEquals(zone, entering.zone());
    assertEquals(zone, exiting.zone());
  }

  @Test
  void debugTypes() {
    assertEquals(
      Set.of(RentalRestrictionExtension.RestrictionType.GEOFENCING_BOUNDARY),
      entering.debugTypes()
    );
  }

  @Test
  void hasRestrictions() {
    assertTrue(entering.hasRestrictions());
  }

  @Test
  void equality() {
    var same = new GeofencingBoundaryExtension(zone, true);
    assertEquals(entering, same);
    assertNotEquals(entering, exiting);
  }
}
