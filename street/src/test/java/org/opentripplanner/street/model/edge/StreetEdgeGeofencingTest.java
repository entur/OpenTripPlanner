package org.opentripplanner.street.model.edge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import org.opentripplanner.service.vehiclerental.street.BusinessAreaBorder;
import org.opentripplanner.service.vehiclerental.street.GeofencingBoundaryExtension;
import org.opentripplanner.service.vehiclerental.street.GeofencingZoneExtension;
import org.opentripplanner.street.model.RentalFormFactor;
import org.opentripplanner.street.model.RentalRestrictionExtension;
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
  static GeofencingZone NO_TRAVERSAL_ZONE = new GeofencingZone(
    new FeedScopedId(NETWORK_TIER, "a-park"),
    null,
    null,
    false,
    true
  );

  static RentalRestrictionExtension NO_DROP_OFF_TIER = new GeofencingZoneExtension(
    NO_DROP_OFF_ZONE_TIER
  );
  static RentalRestrictionExtension NO_TRAVERSAL = new GeofencingZoneExtension(NO_TRAVERSAL_ZONE);

  StreetVertex V1 = intersectionVertex("V1", 0, 0);
  StreetVertex V2 = intersectionVertex("V2", 1, 1);
  StreetVertex V3 = intersectionVertex("V3", 2, 2);
  StreetVertex V4 = intersectionVertex("V4", 3, 3);

  @Test
  public void addTwoExtensions() {
    var edge = streetEdge(V1, V2);
    edge.addRentalRestriction(new BusinessAreaBorder("a"));
    edge.addRentalRestriction(new BusinessAreaBorder("b"));

    assertTrue(edge.fromv.rentalTraversalBanned(forwardState("a")));
    assertTrue(edge.fromv.rentalTraversalBanned(forwardState("b")));
  }

  @Test
  public void removeExtensions() {
    var edge = streetEdge(V1, V2);
    var a = new BusinessAreaBorder("a");
    var b = new BusinessAreaBorder("b");
    var c = new BusinessAreaBorder("c");

    edge.addRentalRestriction(a);

    assertTrue(edge.fromv.rentalRestrictions().traversalBanned(forwardState("a")));

    edge.addRentalRestriction(b);
    edge.addRentalRestriction(c);

    edge.removeRentalExtension(a);

    var restrictions = edge.fromv.rentalRestrictions();
    assertTrue(restrictions.traversalBanned(forwardState("b")));
    assertTrue(restrictions.traversalBanned(forwardState("c")));
    assertFalse(restrictions.traversalBanned(forwardState("a")));

    edge.removeRentalExtension(b);

    assertTrue(edge.fromv.rentalRestrictions().traversalBanned(forwardState("c")));
  }

  @Test
  public void checkNetwork() {
    var edge = streetEdge(V1, V2);
    edge.addRentalRestriction(new BusinessAreaBorder("a"));

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
      var ext = new BusinessAreaBorder(NETWORK_TIER);
      V2.addRentalRestriction(ext);

      var results = traverseFromV1(edge1);

      var onFoot = results[0];
      assertEquals(HAVE_RENTED, onFoot.getVehicleRentalState());
      assertEquals(TraverseMode.WALK, onFoot.getBackMode());
      assertEquals(1, results.length);
    }

    @Test
    public void dontEnterGeofencingZoneOnFoot() {
      var edge = streetEdge(V1, V2);
      V2.addRentalRestriction(
        new GeofencingZoneExtension(
          new GeofencingZone(new FeedScopedId(NETWORK_TIER, "a-park"), null, null, true, true)
        )
      );
      State result = traverseFromV1(edge)[0];
      assertEquals(WALK, result.getBackMode());
      assertEquals(HAVE_RENTED, result.getVehicleRentalState());
    }

    @Test
    public void forkStateWhenEnteringNoDropOffZone() {
      // Set up boundary: V1 is outside, V2 is inside the no-drop-off zone
      V1.addRentalRestriction(new GeofencingBoundaryExtension(NO_DROP_OFF_ZONE_TIER, true));
      V2.addRentalRestriction(new GeofencingBoundaryExtension(NO_DROP_OFF_ZONE_TIER, false));
      // Also add the zone extension on V2 for the old system
      V2.addRentalRestriction(NO_DROP_OFF_TIER);

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
      assertEquals(2, results.length);

      var continueOnFoot = results[0];
      assertEquals(HAVE_RENTED, continueOnFoot.getVehicleRentalState());
      assertEquals(WALK, continueOnFoot.getBackMode());

      var continueRenting = results[1];
      assertEquals(RENTING_FLOATING, continueRenting.getVehicleRentalState());
      assertEquals(SCOOTER, continueRenting.getBackMode());
    }

    @Test
    public void forwardDontFinishInNoDropOffZone() {
      var edge = streetEdge(V1, V2);
      V2.addRentalRestriction(NO_DROP_OFF_TIER);
      edge.addRentalRestriction(NO_DROP_OFF_TIER);
      State result = traverseFromV1(edge)[0];
      assertFalse(result.isFinal());
    }
  }

  @Nested
  class Reverse {

    @Test
    public void backwardDontFinishInNoDropOffZone() {
      var edge = streetEdge(V1, V2);
      edge.addRentalRestriction(NO_DROP_OFF_TIER);
      var state = initialState(V2, NETWORK_TIER, true);
      var state2 = edge.traverse(state)[0];
      assertFalse(state2.isFinal());
    }

    @Test
    public void backwardsDontEnterNoTraversalZone() {
      var edge = streetEdge(V1, V2);
      V2.addRentalRestriction(NO_TRAVERSAL);
      var intialState = initialState(V2, NETWORK_TIER, true);
      var result = edge.traverse(intialState);

      assertTrue(State.isEmpty(result));
      assertNotNull(result);
    }

    @Test
    public void pickupFloatingVehicleWhenLeavingAZone() {
      // Set up: V2 is inside a no-traversal zone, V1 is outside
      // Boundary extensions: V1 has entering=true (V1→V2 enters zone), V2 has entering=false
      V1.addRentalRestriction(new GeofencingBoundaryExtension(NO_TRAVERSAL_ZONE, true));
      V2.addRentalRestriction(new GeofencingBoundaryExtension(NO_TRAVERSAL_ZONE, false));
      V2.addRentalRestriction(NO_TRAVERSAL);

      var req = defaultArriveByRequest();
      var haveRentedState = makeHaveRentedState(V2, req);

      // Verify initial state has zones populated
      assertTrue(haveRentedState.getCurrentGeofencingZones().contains(NO_TRAVERSAL_ZONE));

      var edge = streetEdge(V1, V2);
      var states = edge.traverse(haveRentedState);

      // Walking branch + per-network committed branch + generic
      assertTrue(states.length >= 2);

      var walkingState = Arrays.stream(states)
        .filter(s -> s.getVehicleRentalState() == HAVE_RENTED)
        .findFirst()
        .get();
      assertEquals(WALK, walkingState.currentMode());

      var rentalState = Arrays.stream(states)
        .filter(s -> s.getVehicleRentalState() == RENTING_FLOATING)
        .findFirst()
        .get();
      assertEquals(SCOOTER, rentalState.currentMode());
    }

    @Test
    public void pickupFloatingVehiclesWhenStartedInNoDropOffZone() {
      var states = runTraverse(Collections.emptySet(), Collections.emptySet());

      // Walking + per-network committed branches (tier, bird) + generic
      assertTrue(states.length >= 3);

      final State walkState = Arrays.stream(states)
        .filter(s -> s.getVehicleRentalState() == HAVE_RENTED)
        .findFirst()
        .get();
      assertEquals(WALK, walkState.currentMode());

      // Generic renting state (null network)
      var genericState = Arrays.stream(states)
        .filter(s ->
          s.getVehicleRentalState() == RENTING_FLOATING && s.getVehicleRentalNetwork() == null
        )
        .findFirst();
      assertTrue(genericState.isPresent());
    }

    @Test
    public void pickupFloatingVehiclesWhenAllNetworksBanned() {
      var states = runTraverse(Collections.emptySet(), Set.of(NETWORK_TIER, NETWORK_BIRD));

      // Should only have a walking state — all networks are banned
      assertEquals(1, states.length);
      final State walkState = states[0];
      assertEquals(HAVE_RENTED, walkState.getVehicleRentalState());
      assertEquals(WALK, walkState.currentMode());
    }

    @Test
    public void pickupFloatingVehiclesWhenSomeNetworksBanned() {
      var states = runTraverse(Collections.emptySet(), Set.of(NETWORK_BIRD));

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

    private State[] runTraverse(Set<String> allowedNetworks, Set<String> bannedNetworks) {
      var req = makeArriveByRequest(allowedNetworks, bannedNetworks);

      // V2 inside both tier and bird no-drop-off zones
      V2.addRentalRestriction(NO_DROP_OFF_TIER);
      V2.addRentalRestriction(new GeofencingZoneExtension(NO_DROP_OFF_ZONE_BIRD));

      // Boundary extensions for both zones on V1 and V2
      V1.addRentalRestriction(new GeofencingBoundaryExtension(NO_DROP_OFF_ZONE_TIER, true));
      V2.addRentalRestriction(new GeofencingBoundaryExtension(NO_DROP_OFF_ZONE_TIER, false));
      V1.addRentalRestriction(new GeofencingBoundaryExtension(NO_DROP_OFF_ZONE_BIRD, true));
      V2.addRentalRestriction(new GeofencingBoundaryExtension(NO_DROP_OFF_ZONE_BIRD, false));

      var haveRentedState = makeHaveRentedState(V2, req);

      var edge = streetEdge(V1, V2);

      return edge.traverse(haveRentedState);
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

    private static StreetSearchRequest defaultArriveByRequest() {
      return StreetSearchRequest.of()
        .withScooter(b -> b.withRental(r -> r.withAllowedNetworks(Set.of(NETWORK_TIER))))
        .withMode(StreetMode.SCOOTER_RENTAL)
        .withArriveBy(true)
        .build();
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
}
