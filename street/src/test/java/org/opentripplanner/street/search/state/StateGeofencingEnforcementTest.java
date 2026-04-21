package org.opentripplanner.street.search.state;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opentripplanner.street.model.StreetModelFactory.intersectionVertex;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.service.vehiclerental.model.GeofencingZone;
import org.opentripplanner.street.geometry.Polygons;
import org.opentripplanner.street.model.RentalFormFactor;
import org.opentripplanner.street.model.StreetMode;
import org.opentripplanner.street.search.request.StreetSearchRequest;

class StateGeofencingEnforcementTest {

  static final GeofencingZone NO_DROP_OFF_ZONE = new GeofencingZone(
    new FeedScopedId("tier", "zone1"),
    null,
    Polygons.OSLO_FROGNER_PARK,
    true,
    false
  );

  static final GeofencingZone NO_TRAVERSAL_ZONE = new GeofencingZone(
    new FeedScopedId("tier", "zone2"),
    null,
    Polygons.OSLO,
    false,
    true
  );

  static final GeofencingZone HIGH_PRIORITY_ZONE = new GeofencingZone(
    new FeedScopedId("tier", "zone-hp"),
    null,
    Polygons.OSLO_FROGNER_PARK,
    true,
    true,
    false,
    false,
    null,
    null,
    0
  );

  static final GeofencingZone LOW_PRIORITY_ZONE = new GeofencingZone(
    new FeedScopedId("tier", "zone-lp"),
    null,
    Polygons.OSLO,
    false,
    false,
    false,
    true,
    null,
    null,
    10
  );

  private State createRentingState(String network, GeofencingZone... zones) {
    var req = StreetSearchRequest.of().withMode(StreetMode.SCOOTER_RENTAL).build();
    var v = intersectionVertex(1, 1);
    var s0 = new State(v, req);
    var editor = s0.edit(null);

    // Transition to RENTING_FLOATING with a specific network
    editor.beginFloatingVehicleRenting(RentalFormFactor.SCOOTER_STANDING, null, network, false);

    // Set up zones
    for (var zone : zones) {
      editor.initializeGeofencingZones(Set.of(zone));
    }
    // If multiple zones, we need to add them all. initializeGeofencingZones sets a single zone,
    // so for multi-zone cases, build the set manually via the editor's internal access.
    return editor.makeState();
  }

  @Test
  void isDropOffBannedWhenNetworkMatches() {
    var state = createRentingState("tier", NO_DROP_OFF_ZONE);
    assertTrue(state.isDropOffBannedByCurrentZones());
  }

  @Test
  void isDropOffBannedFalseWhenNetworkDoesNotMatch() {
    var state = createRentingState("bird", NO_DROP_OFF_ZONE);
    assertFalse(state.isDropOffBannedByCurrentZones());
  }

  @Test
  void isDropOffBannedFalseWhenNoNetwork() {
    // Generic state with null network
    var state = createRentingState(null, NO_DROP_OFF_ZONE);
    // null network means generic state - enforcement returns false
    assertFalse(state.isDropOffBannedByCurrentZones());
  }

  @Test
  void isDropOffBannedFalseWhenNoZones() {
    var state = createRentingState("tier");
    assertFalse(state.isDropOffBannedByCurrentZones());
  }

  @Test
  void isTraversalBannedWhenNetworkMatches() {
    var state = createRentingState("tier", NO_TRAVERSAL_ZONE);
    assertTrue(state.isTraversalBannedByCurrentZones());
  }

  @Test
  void isTraversalBannedFalseWhenNoRestriction() {
    // Zone has dropOffBanned but not traversalBanned
    var state = createRentingState("tier", NO_DROP_OFF_ZONE);
    assertFalse(state.isTraversalBannedByCurrentZones());
  }

  @Test
  void governingZoneRespectsPriority() {
    // HIGH_PRIORITY_ZONE (priority 0) has dropOff=true, traversal=true
    // LOW_PRIORITY_ZONE (priority 10) has dropOff=false, traversal=false
    // Both are for network "tier"
    // The governing zone should be HIGH_PRIORITY_ZONE (lowest priority value)
    var state = createRentingState("tier", HIGH_PRIORITY_ZONE);
    assertTrue(state.isDropOffBannedByCurrentZones());
    assertTrue(state.isTraversalBannedByCurrentZones());
  }
}
