package org.opentripplanner.street.search.state;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opentripplanner.street.model.StreetModelFactory.intersectionVertex;
import static org.opentripplanner.street.model.StreetModelFactory.streetEdge;

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
    var v2 = intersectionVertex(2, 2);
    var edge = streetEdge(v, v2);
    var s0 = new State(v, req);
    var editor = s0.edit(edge);

    // Transition to RENTING_FLOATING with a specific network
    editor.beginFloatingVehicleRenting(RentalFormFactor.SCOOTER_STANDING, null, network, false);

    // Set up zones — pass all zones at once
    editor.initializeGeofencingZones(Set.of(zones));
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
    // The high-priority zone's values should win per-field
    var state = createRentingState("tier", HIGH_PRIORITY_ZONE, LOW_PRIORITY_ZONE);
    assertTrue(state.isDropOffBannedByCurrentZones());
    assertTrue(state.isTraversalBannedByCurrentZones());
  }

  // --- Per-field precedence tests ---

  @Test
  void perFieldPrecedenceUnspecifiedFieldFallsThrough() {
    // High-priority zone: traversalBanned=true, dropOffBanned=null (not specified)
    // Low-priority zone: dropOffBanned=true, traversalBanned=null
    // Expected: traversal banned (from high), drop-off banned (from low - per-field fall-through)
    var highPriority = new GeofencingZone(
      new FeedScopedId("tier", "zone-hp"),
      null,
      Polygons.OSLO_FROGNER_PARK,
      null,
      true,
      null,
      false,
      null,
      null,
      0
    );
    var lowPriority = new GeofencingZone(
      new FeedScopedId("tier", "zone-lp"),
      null,
      Polygons.OSLO,
      true,
      null,
      null,
      false,
      null,
      null,
      10
    );
    var state = createRentingState("tier", highPriority, lowPriority);
    assertTrue(state.isTraversalBannedByCurrentZones());
    assertTrue(state.isDropOffBannedByCurrentZones());
  }

  @Test
  void perFieldPrecedenceHighPriorityExplicitlyAllowsOverridesLowPriority() {
    // High-priority zone: dropOffBanned=false (explicitly allowed)
    // Low-priority zone: dropOffBanned=true
    // Expected: drop-off NOT banned (high-priority explicitly allows)
    var highPriority = new GeofencingZone(
      new FeedScopedId("tier", "zone-hp"),
      null,
      Polygons.OSLO_FROGNER_PARK,
      false,
      null,
      null,
      false,
      null,
      null,
      0
    );
    var lowPriority = new GeofencingZone(
      new FeedScopedId("tier", "zone-lp"),
      null,
      Polygons.OSLO,
      true,
      null,
      null,
      false,
      null,
      null,
      10
    );
    var state = createRentingState("tier", highPriority, lowPriority);
    assertFalse(state.isDropOffBannedByCurrentZones());
  }

  @Test
  void nullFieldMeansNotSpecifiedDefaultsToPermissive() {
    // Single zone with dropOffBanned=null, traversalBanned=true
    // Expected: traversal banned, drop-off NOT banned (null = not specified = permissive)
    var zone = new GeofencingZone(
      new FeedScopedId("tier", "zone1"),
      null,
      Polygons.OSLO,
      null,
      true,
      null,
      false,
      null,
      null,
      0
    );
    var state = createRentingState("tier", zone);
    assertTrue(state.isTraversalBannedByCurrentZones());
    assertFalse(state.isDropOffBannedByCurrentZones());
  }
}
