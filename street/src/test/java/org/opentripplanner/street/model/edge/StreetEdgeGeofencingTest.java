package org.opentripplanner.street.model.edge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opentripplanner.street.model.StreetModelFactory.intersectionVertex;
import static org.opentripplanner.street.model.StreetModelFactory.streetEdge;
import static org.opentripplanner.street.search.TraverseMode.SCOOTER;
import static org.opentripplanner.street.search.TraverseMode.WALK;
import static org.opentripplanner.street.search.state.VehicleRentalState.HAVE_RENTED;
import static org.opentripplanner.street.search.state.VehicleRentalState.RENTING_FLOATING;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.service.vehiclerental.model.GeofencingZone;
import org.opentripplanner.service.vehiclerental.model.RentalVehicleType.PropulsionType;
import org.opentripplanner.service.vehiclerental.street.GeofencingBoundaryExtension;
import org.opentripplanner.street.model.RentalFormFactor;
import org.opentripplanner.street.model.StreetMode;
import org.opentripplanner.street.model.vertex.StreetVertex;
import org.opentripplanner.street.model.vertex.Vertex;
import org.opentripplanner.street.search.TraverseMode;
import org.opentripplanner.street.search.request.StreetSearchRequest;
import org.opentripplanner.street.search.state.State;
import org.opentripplanner.street.search.state.StateEditor;

class StreetEdgeGeofencingTest {

  static String NETWORK_TIER = "tier-oslo";
  static String NETWORK_BIRD = "bird-oslo";

  static GeofencingZone NO_DROP_OFF_ZONE_TIER = new GeofencingZone(
    new FeedScopedId(NETWORK_TIER, "a-park"),
    null,
    null,
    true,
    false
  );

  static GeofencingZone NO_DROP_OFF_ZONE_BIRD = new GeofencingZone(
    new FeedScopedId(NETWORK_BIRD, "a-park"),
    null,
    null,
    true,
    false
  );

  static GeofencingZone NO_DROP_OFF_ZONE_TIER_2 = new GeofencingZone(
    new FeedScopedId(NETWORK_TIER, "b-park"),
    null,
    null,
    true,
    false
  );

  static GeofencingZone NO_TRAVERSAL_ZONE = new GeofencingZone(
    new FeedScopedId(NETWORK_TIER, "no-traverse"),
    null,
    null,
    false,
    true
  );

  StreetVertex V1 = intersectionVertex("V1", 0, 0);
  StreetVertex V2 = intersectionVertex("V2", 1, 1);
  StreetVertex V3 = intersectionVertex("V3", 2, 2);
  StreetVertex V4 = intersectionVertex("V4", 3, 3);

  @Test
  public void addBusinessAreaBorderNetwork() {
    var edge = streetEdge(V1, V2);
    edge.addBusinessAreaBorderNetwork("a");

    assertTrue(edge.fromv.rentalTraversalBanned(forwardState("a")));
    assertFalse(edge.fromv.rentalTraversalBanned(forwardState("b")));
  }

  @Test
  public void removeBusinessAreaBorderNetwork() {
    var edge = streetEdge(V1, V2);
    edge.addBusinessAreaBorderNetwork("a");

    assertTrue(edge.fromv.rentalTraversalBanned(forwardState("a")));

    edge.removeBusinessAreaBorderNetwork("a");

    assertFalse(edge.fromv.rentalTraversalBanned(forwardState("a")));
  }

  @Test
  public void checkNetwork() {
    var edge = streetEdge(V1, V2);
    edge.addBusinessAreaBorderNetwork("a");

    var state = traverseFromV1(edge);

    assertEquals(RENTING_FLOATING, state[0].getVehicleRentalState());
    assertEquals(1, state.length);
  }

  @Nested
  class Forward {

    @Test
    public void finishInEdgeWithoutRestrictions() {
      var edge = streetEdge(V1, V2);
      var result = traverseFromV1(edge)[0];
      assertTrue(result.isFinal());
    }

    @Test
    public void leaveBusinessAreaOnFoot() {
      var edge1 = streetEdge(V1, V2);
      V2.addBusinessAreaBorderNetwork(NETWORK_TIER);

      var results = traverseFromV1(edge1);

      var onFoot = results[0];
      assertEquals(HAVE_RENTED, onFoot.getVehicleRentalState());
      assertEquals(TraverseMode.WALK, onFoot.getBackMode());
      assertEquals(1, results.length);
    }

    /**
     * When the rider starts AT the boundary vertex and traverses into a no-drop-off zone,
     * they continue riding normally. No drop-off is offered inside the zone — the
     * pre-traversal fork on the approach edge handles the drop-at-boundary case.
     */
    @Test
    public void continueRidingWhenStartingAtNoDropOffZoneBoundary() {
      // Set up boundary: V1 is outside, V2 is inside the no-drop-off zone
      V1.addGeofencingBoundary(new GeofencingBoundaryExtension(NO_DROP_OFF_ZONE_TIER, true));
      V2.addGeofencingBoundary(new GeofencingBoundaryExtension(NO_DROP_OFF_ZONE_TIER, false));

      var edge = streetEdge(V1, V2);

      var req = StreetSearchRequest.of().withMode(StreetMode.SCOOTER_RENTAL).build();
      var editor = new StateEditor(V1, req);
      editor.beginFloatingVehicleRenting(
        RentalFormFactor.SCOOTER,
        PropulsionType.ELECTRIC,
        NETWORK_TIER,
        false
      );

      var results = edge.traverse(editor.makeState());
      assertEquals(1, results.length);
      assertEquals(RENTING_FLOATING, results[0].getVehicleRentalState());
      assertEquals(SCOOTER, results[0].getBackMode());
    }

    /**
     * When the rider approaches a no-drop-off zone boundary from one edge before, the
     * pre-traversal check forks: one branch drops at the boundary vertex (outside zone),
     * the other continues riding into the zone. The A* picks the best option based on
     * whether the destination is inside or outside the zone.
     */
    @Test
    public void forkWhenApproachingNoDropOffZone() {
      // V1→V2→V3, boundary at V2/V3. Rider approaches from V1.
      V2.addGeofencingBoundary(new GeofencingBoundaryExtension(NO_DROP_OFF_ZONE_TIER, true));
      V3.addGeofencingBoundary(new GeofencingBoundaryExtension(NO_DROP_OFF_ZONE_TIER, false));

      var approachEdge = streetEdge(V1, V2);

      var req = StreetSearchRequest.of().withMode(StreetMode.SCOOTER_RENTAL).build();
      var editor = new StateEditor(V1, req);
      editor.beginFloatingVehicleRenting(
        RentalFormFactor.SCOOTER,
        PropulsionType.ELECTRIC,
        NETWORK_TIER,
        false
      );

      var results = approachEdge.traverse(editor.makeState());
      assertEquals(2, results.length);

      var droppedOff = results[0];
      assertEquals(HAVE_RENTED, droppedOff.getVehicleRentalState());
      assertEquals(SCOOTER, droppedOff.getBackMode());

      var continueRiding = results[1];
      assertEquals(RENTING_FLOATING, continueRiding.getVehicleRentalState());
      assertEquals(SCOOTER, continueRiding.getBackMode());
    }

    /**
     * A committed rider approaching a no-traversal zone boundary is forced to drop the
     * vehicle and walk. Tests the pre-traversal trigger at line 344.
     */
    @Test
    public void forwardNoTraversalBoundaryForcesDropForCommittedRider() {
      V1.addGeofencingBoundary(new GeofencingBoundaryExtension(NO_TRAVERSAL_ZONE, true));
      V2.addGeofencingBoundary(new GeofencingBoundaryExtension(NO_TRAVERSAL_ZONE, false));

      var edge = streetEdge(V1, V2);
      var state = initialState(V1, NETWORK_TIER, false);

      var results = edge.traverse(state);
      assertEquals(1, results.length);
      assertEquals(HAVE_RENTED, results[0].getVehicleRentalState());
    }

    /**
     * When a committed rider already has a no-traversal zone in state (from a previous
     * boundary crossing), subsequent edges force drop+walk via isTraversalBannedByCurrentZones.
     */
    @Test
    public void forwardTraversalBanWhenZoneAlreadyInState() {
      // No boundaries on vertices — the zone is already in state
      var edge = streetEdge(V1, V2);
      var state = initialStateWithZones(V1, NETWORK_TIER, false, Set.of(NO_TRAVERSAL_ZONE));

      var results = edge.traverse(state);
      assertEquals(1, results.length);
      assertEquals(HAVE_RENTED, results[0].getVehicleRentalState());
      assertEquals(WALK, results[0].getBackMode());
    }

    /**
     * When a rider is already inside a no-drop-off zone and approaches a second one,
     * no fork is offered — the rider continues riding through.
     */
    @Test
    public void noForkWhenAlreadyInsideNoDropOffZone() {
      // V2→V3 boundary for a second no-drop-off zone
      V2.addGeofencingBoundary(new GeofencingBoundaryExtension(NO_DROP_OFF_ZONE_TIER_2, true));
      V3.addGeofencingBoundary(new GeofencingBoundaryExtension(NO_DROP_OFF_ZONE_TIER_2, false));

      var edge = streetEdge(V1, V2);
      // Rider already inside the first no-drop-off zone
      var state = initialStateWithZones(V1, NETWORK_TIER, false, Set.of(NO_DROP_OFF_ZONE_TIER));

      var results = edge.traverse(state);
      assertEquals(1, results.length);
      assertEquals(RENTING_FLOATING, results[0].getVehicleRentalState());
      assertEquals(SCOOTER, results[0].getBackMode());
    }

    /**
     * A generic (null-network) RENTING_FLOATING crossing a zone boundary forks into
     * per-network committed branches plus a generic continuation.
     */
    @Test
    public void genericStateForkAtZoneBoundary() {
      V1.addGeofencingBoundary(new GeofencingBoundaryExtension(NO_DROP_OFF_ZONE_TIER, true));
      V2.addGeofencingBoundary(new GeofencingBoundaryExtension(NO_DROP_OFF_ZONE_TIER, false));

      var edge = streetEdge(V1, V2);
      var state = initialState(V1, null, false);

      var results = edge.traverse(state);
      assertEquals(2, results.length);

      // Committed branch for TIER network
      var committed = Arrays.stream(results)
        .filter(s -> NETWORK_TIER.equals(s.getVehicleRentalNetwork()))
        .findFirst();
      assertTrue(committed.isPresent());
      assertEquals(RENTING_FLOATING, committed.get().getVehicleRentalState());

      // Generic continuation with updated committedNetworks
      var generic = Arrays.stream(results)
        .filter(s -> s.getVehicleRentalNetwork() == null)
        .findFirst();
      assertTrue(generic.isPresent());
      assertTrue(generic.get().getCommittedNetworks().contains(NETWORK_TIER));
    }

    /**
     * When a generic state has already committed TIER, crossing a TIER zone boundary
     * should not create a duplicate committed branch.
     */
    @Test
    public void genericStateForkSkipsAlreadyCommittedNetwork() {
      V1.addGeofencingBoundary(new GeofencingBoundaryExtension(NO_DROP_OFF_ZONE_TIER, true));
      V2.addGeofencingBoundary(new GeofencingBoundaryExtension(NO_DROP_OFF_ZONE_TIER, false));

      var edge = streetEdge(V1, V2);
      var state = initialStateWithCommittedNetworks(V1, Set.of(NETWORK_TIER));

      var results = edge.traverse(state);
      // Only the generic continuation — no new committed branch
      assertEquals(1, results.length);
      assertEquals(RENTING_FLOATING, results[0].getVehicleRentalState());
      // Still generic (null network)
      assertEquals(null, results[0].getVehicleRentalNetwork());
    }

    /**
     * A generic state crossing a no-traversal zone boundary does not trigger the generic
     * fork (no-traversal zones are skipped by isGenericBoundaryForkTrigger). Instead, the
     * post-traversal zone entry trigger detects the no-traversal zone and force-drops.
     */
    @Test
    public void genericStateForcedDropAtNoTraversalZone() {
      V1.addGeofencingBoundary(new GeofencingBoundaryExtension(NO_TRAVERSAL_ZONE, true));
      V2.addGeofencingBoundary(new GeofencingBoundaryExtension(NO_TRAVERSAL_ZONE, false));

      var edge = streetEdge(V1, V2);
      var state = initialState(V1, null, false);

      var results = edge.traverse(state);
      // Generic state: tov.isGeofencingNoTraversalBoundary returns false (null network),
      // but updateGeofencingZones adds the zone to state. Then isForwardZoneEntryTrigger
      // fires (traversalBanned=true, network=null matches), forcing a drop.
      assertEquals(1, results.length);
      assertEquals(HAVE_RENTED, results[0].getVehicleRentalState());
    }

    /**
     * A committed RENTING_FLOATING state at a no-drop-off boundary takes the drop/ride
     * fork, not the generic boundary fork. The generic fork only fires for null-network states.
     */
    @Test
    public void committedStateDoesNotTriggerGenericFork() {
      // Boundary on V2 (entering=true) / V3 (entering=false). Edge V1→V2, tov=V2.
      V2.addGeofencingBoundary(new GeofencingBoundaryExtension(NO_DROP_OFF_ZONE_TIER, true));
      V3.addGeofencingBoundary(new GeofencingBoundaryExtension(NO_DROP_OFF_ZONE_TIER, false));

      var edge = streetEdge(V1, V2);
      var state = initialState(V1, NETWORK_TIER, false);

      var results = edge.traverse(state);
      // Drop + ride branches from the no-drop-off fork (not generic fork)
      assertEquals(2, results.length);
      var dropped = Arrays.stream(results)
        .filter(s -> s.getVehicleRentalState() == HAVE_RENTED)
        .findFirst();
      assertTrue(dropped.isPresent());
      var riding = Arrays.stream(results)
        .filter(s -> s.getVehicleRentalState() == RENTING_FLOATING)
        .findFirst();
      assertTrue(riding.isPresent());
    }
  }

  @Nested
  class Reverse {

    @Test
    public void pickupFloatingVehicleWhenLeavingNoTraversalZone() {
      // 3-vertex graph: V3→V1→V2. Boundary at V1/V2, V2 inside no-traversal zone.
      // Boundary fork fires on V1→V2 (walking only), deferred fork fires on V3→V1.
      V1.addGeofencingBoundary(new GeofencingBoundaryExtension(NO_TRAVERSAL_ZONE, true));
      V2.addGeofencingBoundary(new GeofencingBoundaryExtension(NO_TRAVERSAL_ZONE, false));

      var boundaryEdge = streetEdge(V1, V2);
      var deferredEdge = streetEdge(V3, V1);
      var req = makeArriveByRequest(Set.of(NETWORK_TIER), Collections.emptySet());
      var haveRentedState = makeHaveRentedState(V2, req, Set.of(NO_TRAVERSAL_ZONE));

      assertTrue(haveRentedState.getCurrentGeofencingZones().contains(NO_TRAVERSAL_ZONE));

      // Step 1: boundary fork on V1→V2 — only produces walking branch
      var boundaryStates = boundaryEdge.traverse(haveRentedState);
      assertEquals(1, boundaryStates.length);
      var walkingAtV1 = boundaryStates[0];
      assertEquals(HAVE_RENTED, walkingAtV1.getVehicleRentalState());
      assertEquals(WALK, walkingAtV1.currentMode());

      // Step 2: deferred fork on V3→V1 — produces walking + RENTING_FLOATING
      var states = deferredEdge.traverse(walkingAtV1);
      assertTrue(states.length >= 2);

      // Walking branch
      var walkingState = Arrays.stream(states)
        .filter(s -> s.getVehicleRentalState() == HAVE_RENTED)
        .findFirst()
        .get();
      assertEquals(WALK, walkingState.currentMode());

      // Renting branch (speculative pickup)
      var rentalState = Arrays.stream(states)
        .filter(s -> s.getVehicleRentalState() == RENTING_FLOATING)
        .findFirst()
        .get();
      assertEquals(SCOOTER, rentalState.currentMode());
    }

    @Test
    public void pickupFloatingVehiclesWhenStartedInNoDropOffZone() {
      // 3-vertex graph: V3→V1→V2. Boundary at V1/V2, V2 inside both zones.
      V1.addGeofencingBoundary(new GeofencingBoundaryExtension(NO_DROP_OFF_ZONE_TIER, true));
      V2.addGeofencingBoundary(new GeofencingBoundaryExtension(NO_DROP_OFF_ZONE_TIER, false));
      V1.addGeofencingBoundary(new GeofencingBoundaryExtension(NO_DROP_OFF_ZONE_BIRD, true));
      V2.addGeofencingBoundary(new GeofencingBoundaryExtension(NO_DROP_OFF_ZONE_BIRD, false));

      var boundaryEdge = streetEdge(V1, V2);
      var deferredEdge = streetEdge(V3, V1);
      var req = makeArriveByRequest(Collections.emptySet(), Collections.emptySet());
      var haveRentedState = makeHaveRentedState(
        V2,
        req,
        Set.of(NO_DROP_OFF_ZONE_TIER, NO_DROP_OFF_ZONE_BIRD)
      );

      // Step 1: boundary fork — walking only
      var boundaryStates = boundaryEdge.traverse(haveRentedState);
      assertEquals(1, boundaryStates.length);
      var walkingAtV1 = boundaryStates[0];
      assertEquals(HAVE_RENTED, walkingAtV1.getVehicleRentalState());

      // Step 2: deferred fork — walking + per-network + generic
      var states = deferredEdge.traverse(walkingAtV1);

      // Should have: walking + per-network committed branches + generic
      assertTrue(states.length >= 3);

      // Walking branch
      var walkState = Arrays.stream(states)
        .filter(s -> s.getVehicleRentalState() == HAVE_RENTED)
        .findFirst()
        .get();
      assertEquals(WALK, walkState.currentMode());

      // Generic renting state (null network)
      var genericState = Arrays.stream(states)
        .filter(
          s -> s.getVehicleRentalState() == RENTING_FLOATING && s.getVehicleRentalNetwork() == null
        )
        .findFirst();
      assertTrue(genericState.isPresent());
    }

    @Test
    public void pickupFloatingVehiclesWhenAllNetworksBanned() {
      V1.addGeofencingBoundary(new GeofencingBoundaryExtension(NO_DROP_OFF_ZONE_TIER, true));
      V2.addGeofencingBoundary(new GeofencingBoundaryExtension(NO_DROP_OFF_ZONE_TIER, false));
      V1.addGeofencingBoundary(new GeofencingBoundaryExtension(NO_DROP_OFF_ZONE_BIRD, true));
      V2.addGeofencingBoundary(new GeofencingBoundaryExtension(NO_DROP_OFF_ZONE_BIRD, false));

      var edge = streetEdge(V1, V2);
      var req = makeArriveByRequest(Collections.emptySet(), Set.of(NETWORK_TIER, NETWORK_BIRD));
      var haveRentedState = makeHaveRentedState(
        V2,
        req,
        Set.of(NO_DROP_OFF_ZONE_TIER, NO_DROP_OFF_ZONE_BIRD)
      );

      var states = edge.traverse(haveRentedState);

      // All networks banned — only walking state
      assertEquals(1, states.length);
      assertEquals(HAVE_RENTED, states[0].getVehicleRentalState());
      assertEquals(WALK, states[0].currentMode());
    }

    @Test
    public void pickupFloatingVehiclesWhenSomeNetworksBanned() {
      // 3-vertex graph: V3→V1→V2. Boundary at V1/V2, V2 inside both zones.
      V1.addGeofencingBoundary(new GeofencingBoundaryExtension(NO_DROP_OFF_ZONE_TIER, true));
      V2.addGeofencingBoundary(new GeofencingBoundaryExtension(NO_DROP_OFF_ZONE_TIER, false));
      V1.addGeofencingBoundary(new GeofencingBoundaryExtension(NO_DROP_OFF_ZONE_BIRD, true));
      V2.addGeofencingBoundary(new GeofencingBoundaryExtension(NO_DROP_OFF_ZONE_BIRD, false));

      var boundaryEdge = streetEdge(V1, V2);
      var deferredEdge = streetEdge(V3, V1);
      // Bird is banned, tier is allowed
      var req = makeArriveByRequest(Collections.emptySet(), Set.of(NETWORK_BIRD));
      var haveRentedState = makeHaveRentedState(
        V2,
        req,
        Set.of(NO_DROP_OFF_ZONE_TIER, NO_DROP_OFF_ZONE_BIRD)
      );

      // Step 1: boundary fork — walking only
      var boundaryStates = boundaryEdge.traverse(haveRentedState);
      assertEquals(1, boundaryStates.length);

      // Step 2: deferred fork — walking + tier (bird banned)
      var states = deferredEdge.traverse(boundaryStates[0]);

      // Walking + tier committed + generic (bird is banned)
      assertTrue(states.length >= 2);

      var tierState = Arrays.stream(states)
        .filter(s -> NETWORK_TIER.equals(s.getVehicleRentalNetwork()))
        .findFirst();
      assertTrue(tierState.isPresent());

      var birdState = Arrays.stream(states)
        .filter(s -> NETWORK_BIRD.equals(s.getVehicleRentalNetwork()))
        .findFirst();
      assertFalse(birdState.isPresent());
    }

    @Test
    public void pickupFloatingVehiclesWithAllowedNetworkFilter() {
      // 3-vertex graph: V3→V1→V2. Boundary at V1/V2, V2 inside both zones.
      V1.addGeofencingBoundary(new GeofencingBoundaryExtension(NO_DROP_OFF_ZONE_TIER, true));
      V2.addGeofencingBoundary(new GeofencingBoundaryExtension(NO_DROP_OFF_ZONE_TIER, false));
      V1.addGeofencingBoundary(new GeofencingBoundaryExtension(NO_DROP_OFF_ZONE_BIRD, true));
      V2.addGeofencingBoundary(new GeofencingBoundaryExtension(NO_DROP_OFF_ZONE_BIRD, false));

      var boundaryEdge = streetEdge(V1, V2);
      var deferredEdge = streetEdge(V3, V1);
      // Only tier is allowed
      var req = makeArriveByRequest(Set.of(NETWORK_TIER), Collections.emptySet());
      var haveRentedState = makeHaveRentedState(
        V2,
        req,
        Set.of(NO_DROP_OFF_ZONE_TIER, NO_DROP_OFF_ZONE_BIRD)
      );

      // Step 1: boundary fork — walking only
      var boundaryStates = boundaryEdge.traverse(haveRentedState);
      assertEquals(1, boundaryStates.length);

      // Step 2: deferred fork — walking + tier (bird not in allowed list)
      var states = deferredEdge.traverse(boundaryStates[0]);

      var tierState = Arrays.stream(states)
        .filter(s -> NETWORK_TIER.equals(s.getVehicleRentalNetwork()))
        .findFirst();
      assertTrue(tierState.isPresent());

      var birdState = Arrays.stream(states)
        .filter(s -> NETWORK_BIRD.equals(s.getVehicleRentalNetwork()))
        .findFirst();
      assertFalse(birdState.isPresent());
    }

    /**
     * Create a HAVE_RENTED walker state with pre-populated currentGeofencingZones,
     * simulating an arriveBy initial state at a destination inside geofencing zones.
     */
    private State makeHaveRentedState(
      Vertex vertex,
      StreetSearchRequest req,
      Set<GeofencingZone> zones
    ) {
      var reqWithZones = StreetSearchRequest.copyOf(req)
        .withArriveByDestinationZones(zones)
        .build();
      return State.getInitialStates(Set.of(vertex), reqWithZones)
        .stream()
        .filter(s -> s.getVehicleRentalState() == HAVE_RENTED)
        .findAny()
        .get();
    }

    @Test
    public void arriveByRentingBlockedFromEnteringNoTraversalZone() {
      // V1 inside no-traversal zone (entering=false), V2 boundary outside (entering=true). Paired.
      V1.addGeofencingBoundary(new GeofencingBoundaryExtension(NO_TRAVERSAL_ZONE, false));
      V2.addGeofencingBoundary(new GeofencingBoundaryExtension(NO_TRAVERSAL_ZONE, true));

      var edge = streetEdge(V1, V2);
      // RENTING_FLOATING rider at V2 (tov), arrive-by traversal goes from V2 to V1 (into zone)
      var rentingState = initialState(V2, NETWORK_TIER, true);

      var states = edge.traverse(rentingState);
      assertEquals(0, states.length);
    }

    @Test
    public void arriveByRentingBlockedOnInteriorEdgeAdjacentToBoundary() {
      // V1 is boundary vertex (entering=false, inside zone). V3 is interior (no boundary).
      // Edge V1→V3: fromv=V1 has boundary, tov=V3 has none (not paired).
      // Arrive-by traversal from V3 to V1 should still be blocked — the rider would
      // enter the zone even though V3 has no boundary extension.
      V1.addGeofencingBoundary(new GeofencingBoundaryExtension(NO_TRAVERSAL_ZONE, false));

      var edge = streetEdge(V1, V3);
      var rentingState = initialState(V3, NETWORK_TIER, true);

      var states = edge.traverse(rentingState);
      assertEquals(0, states.length);
    }

    @Test
    public void arriveByRentingNotBlockedForDifferentNetwork() {
      V1.addGeofencingBoundary(new GeofencingBoundaryExtension(NO_TRAVERSAL_ZONE, false));
      V2.addGeofencingBoundary(new GeofencingBoundaryExtension(NO_TRAVERSAL_ZONE, true));

      var edge = streetEdge(V1, V2);
      // Rider on BIRD network — zone is for TIER, should not be blocked
      var rentingState = initialState(V2, NETWORK_BIRD, true);

      var states = edge.traverse(rentingState);
      assertTrue(states.length > 0);
    }

    @Test
    public void arriveByGenericRentingBlockedByNoTraversalZone() {
      V1.addGeofencingBoundary(new GeofencingBoundaryExtension(NO_TRAVERSAL_ZONE, false));
      V2.addGeofencingBoundary(new GeofencingBoundaryExtension(NO_TRAVERSAL_ZONE, true));

      var edge = streetEdge(V1, V2);
      // Generic state (null network) should also be blocked — no-traversal applies to all vehicles
      var rentingState = initialState(V2, null, true);

      var states = edge.traverse(rentingState);
      assertEquals(0, states.length);
    }

    /**
     * When a HAVE_RENTED walker has no zones in state, the arriveBy boundary fork trigger
     * returns false (early return). Normal walking traversal occurs.
     */
    @Test
    public void arriveByBoundaryForkNoOpWhenNoZonesInState() {
      V1.addGeofencingBoundary(new GeofencingBoundaryExtension(NO_DROP_OFF_ZONE_TIER, true));
      V2.addGeofencingBoundary(new GeofencingBoundaryExtension(NO_DROP_OFF_ZONE_TIER, false));

      var edge = streetEdge(V1, V2);
      var req = makeArriveByRequest(Collections.emptySet(), Collections.emptySet());
      // HAVE_RENTED walker with EMPTY zones
      var haveRentedState = makeHaveRentedState(V2, req, Collections.emptySet());

      var states = edge.traverse(haveRentedState);
      assertEquals(1, states.length);
      assertEquals(HAVE_RENTED, states[0].getVehicleRentalState());
      assertEquals(WALK, states[0].currentMode());
    }

    /**
     * When a walker exits one zone but remains inside another (overlapping zones),
     * the deferred fork only creates branches for the exited zone's network.
     */
    @Test
    public void deferredForkOnlyForksExitedZoneNetworks() {
      // Boundary at V1/V2 for TIER zone only (not BIRD)
      V1.addGeofencingBoundary(new GeofencingBoundaryExtension(NO_DROP_OFF_ZONE_TIER, true));
      V2.addGeofencingBoundary(new GeofencingBoundaryExtension(NO_DROP_OFF_ZONE_TIER, false));

      var boundaryEdge = streetEdge(V1, V2);
      var deferredEdge = streetEdge(V4, V1);
      var req = makeArriveByRequest(Collections.emptySet(), Collections.emptySet());
      // Walker inside both TIER and BIRD zones
      var haveRentedState = makeHaveRentedState(
        V2,
        req,
        Set.of(NO_DROP_OFF_ZONE_TIER, NO_DROP_OFF_ZONE_BIRD)
      );

      // Step 1: boundary fork — exits TIER zone, still inside BIRD
      var boundaryStates = boundaryEdge.traverse(haveRentedState);
      assertEquals(1, boundaryStates.length);
      var walkingAtV1 = boundaryStates[0];
      // TIER was exited, BIRD remains
      assertTrue(walkingAtV1.getCurrentGeofencingZones().contains(NO_DROP_OFF_ZONE_BIRD));
      assertFalse(walkingAtV1.getCurrentGeofencingZones().contains(NO_DROP_OFF_ZONE_TIER));

      // Step 2: deferred fork — should fork TIER only (not BIRD)
      var states = deferredEdge.traverse(walkingAtV1);
      assertTrue(states.length >= 2);

      // TIER committed branch exists
      var tierState = Arrays.stream(states)
        .filter(s -> NETWORK_TIER.equals(s.getVehicleRentalNetwork()))
        .findFirst();
      assertTrue(tierState.isPresent());

      // No BIRD committed branch (BIRD zone was not exited)
      var birdState = Arrays.stream(states)
        .filter(s -> NETWORK_BIRD.equals(s.getVehicleRentalNetwork()))
        .findFirst();
      assertFalse(birdState.isPresent());
    }

    private static StreetSearchRequest makeArriveByRequest(
      Set<String> allowedNetworks,
      Set<String> bannedNetworks
    ) {
      return StreetSearchRequest.of()
        .withScooter(b ->
          b.withRental(r ->
            r.withAllowedNetworks(allowedNetworks).withBannedNetworks(bannedNetworks)
          )
        )
        .withMode(StreetMode.SCOOTER_RENTAL)
        .withArriveBy(true)
        .build();
    }
  }

  private State[] traverseFromV1(StreetEdge edge) {
    var state = initialState(V1, NETWORK_TIER, false);
    return edge.traverse(state);
  }

  private State forwardState(String network) {
    return initialState(V1, network, false);
  }

  private State initialState(Vertex startVertex, String network, boolean arriveBy) {
    var req = StreetSearchRequest.of()
      .withMode(StreetMode.SCOOTER_RENTAL)
      .withArriveBy(arriveBy)
      .build();
    var editor = new StateEditor(startVertex, req);
    editor.beginFloatingVehicleRenting(
      RentalFormFactor.SCOOTER,
      PropulsionType.ELECTRIC,
      network,
      false
    );
    return editor.makeState();
  }

  private State initialStateWithZones(
    Vertex startVertex,
    String network,
    boolean arriveBy,
    Set<GeofencingZone> zones
  ) {
    var req = StreetSearchRequest.of()
      .withMode(StreetMode.SCOOTER_RENTAL)
      .withArriveBy(arriveBy)
      .build();
    var editor = new StateEditor(startVertex, req);
    editor.beginFloatingVehicleRenting(
      RentalFormFactor.SCOOTER,
      PropulsionType.ELECTRIC,
      network,
      false
    );
    editor.initializeGeofencingZones(zones);
    return editor.makeState();
  }

  private State initialStateWithCommittedNetworks(
    Vertex startVertex,
    Set<String> committedNetworks
  ) {
    var req = StreetSearchRequest.of().withMode(StreetMode.SCOOTER_RENTAL).build();
    var editor = new StateEditor(startVertex, req);
    editor.beginFloatingVehicleRenting(
      RentalFormFactor.SCOOTER,
      PropulsionType.ELECTRIC,
      null,
      false
    );
    editor.setCommittedNetworks(committedNetworks);
    return editor.makeState();
  }
}
