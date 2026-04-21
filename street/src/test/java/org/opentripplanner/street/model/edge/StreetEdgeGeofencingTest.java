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
import org.opentripplanner.service.vehiclerental.street.BusinessAreaBorder;
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
  public void setBusinessAreaBorder() {
    var edge = streetEdge(V1, V2);
    edge.setBusinessAreaBorder(new BusinessAreaBorder("a"));

    assertTrue(edge.fromv.rentalTraversalBanned(forwardState("a")));
    assertFalse(edge.fromv.rentalTraversalBanned(forwardState("b")));
  }

  @Test
  public void removeBusinessAreaBorder() {
    var edge = streetEdge(V1, V2);
    edge.setBusinessAreaBorder(new BusinessAreaBorder("a"));

    assertTrue(edge.fromv.rentalTraversalBanned(forwardState("a")));

    edge.removeBusinessAreaBorder();

    assertFalse(edge.fromv.rentalTraversalBanned(forwardState("a")));
  }

  @Test
  public void checkNetwork() {
    var edge = streetEdge(V1, V2);
    edge.setBusinessAreaBorder(new BusinessAreaBorder("a"));

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
      V2.setBusinessAreaBorder(ext);

      var results = traverseFromV1(edge1);

      var onFoot = results[0];
      assertEquals(HAVE_RENTED, onFoot.getVehicleRentalState());
      assertEquals(TraverseMode.WALK, onFoot.getBackMode());
      assertEquals(1, results.length);
    }

    @Test
    public void forkStateWhenEnteringNoDropOffZone() {
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
      assertEquals(2, results.length);

      var continueOnFoot = results[0];
      assertEquals(HAVE_RENTED, continueOnFoot.getVehicleRentalState());
      assertEquals(WALK, continueOnFoot.getBackMode());

      var continueRenting = results[1];
      assertEquals(RENTING_FLOATING, continueRenting.getVehicleRentalState());
      assertEquals(SCOOTER, continueRenting.getBackMode());
    }
  }

  @Nested
  class Reverse {

    @Test
    public void pickupFloatingVehicleWhenLeavingNoTraversalZone() {
      // V2 inside no-traversal zone, V1 outside. Paired boundary extensions.
      V1.addGeofencingBoundary(new GeofencingBoundaryExtension(NO_TRAVERSAL_ZONE, true));
      V2.addGeofencingBoundary(new GeofencingBoundaryExtension(NO_TRAVERSAL_ZONE, false));

      var edge = streetEdge(V1, V2);
      var req = makeArriveByRequest(Set.of(NETWORK_TIER), Collections.emptySet());
      var haveRentedState = makeHaveRentedState(V2, req, Set.of(NO_TRAVERSAL_ZONE));

      assertTrue(haveRentedState.getCurrentGeofencingZones().contains(NO_TRAVERSAL_ZONE));

      var states = edge.traverse(haveRentedState);
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
      // V2 inside both tier and bird no-drop-off zones. Paired boundaries on V1/V2.
      V1.addGeofencingBoundary(new GeofencingBoundaryExtension(NO_DROP_OFF_ZONE_TIER, true));
      V2.addGeofencingBoundary(new GeofencingBoundaryExtension(NO_DROP_OFF_ZONE_TIER, false));
      V1.addGeofencingBoundary(new GeofencingBoundaryExtension(NO_DROP_OFF_ZONE_BIRD, true));
      V2.addGeofencingBoundary(new GeofencingBoundaryExtension(NO_DROP_OFF_ZONE_BIRD, false));

      var edge = streetEdge(V1, V2);
      var req = makeArriveByRequest(Collections.emptySet(), Collections.emptySet());
      var haveRentedState = makeHaveRentedState(
        V2,
        req,
        Set.of(NO_DROP_OFF_ZONE_TIER, NO_DROP_OFF_ZONE_BIRD)
      );

      var states = edge.traverse(haveRentedState);

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
      V1.addGeofencingBoundary(new GeofencingBoundaryExtension(NO_DROP_OFF_ZONE_TIER, true));
      V2.addGeofencingBoundary(new GeofencingBoundaryExtension(NO_DROP_OFF_ZONE_TIER, false));
      V1.addGeofencingBoundary(new GeofencingBoundaryExtension(NO_DROP_OFF_ZONE_BIRD, true));
      V2.addGeofencingBoundary(new GeofencingBoundaryExtension(NO_DROP_OFF_ZONE_BIRD, false));

      var edge = streetEdge(V1, V2);
      // Bird is banned, tier is allowed
      var req = makeArriveByRequest(Collections.emptySet(), Set.of(NETWORK_BIRD));
      var haveRentedState = makeHaveRentedState(
        V2,
        req,
        Set.of(NO_DROP_OFF_ZONE_TIER, NO_DROP_OFF_ZONE_BIRD)
      );

      var states = edge.traverse(haveRentedState);

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
      V1.addGeofencingBoundary(new GeofencingBoundaryExtension(NO_DROP_OFF_ZONE_TIER, true));
      V2.addGeofencingBoundary(new GeofencingBoundaryExtension(NO_DROP_OFF_ZONE_TIER, false));
      V1.addGeofencingBoundary(new GeofencingBoundaryExtension(NO_DROP_OFF_ZONE_BIRD, true));
      V2.addGeofencingBoundary(new GeofencingBoundaryExtension(NO_DROP_OFF_ZONE_BIRD, false));

      var edge = streetEdge(V1, V2);
      // Only tier is allowed
      var req = makeArriveByRequest(Set.of(NETWORK_TIER), Collections.emptySet());
      var haveRentedState = makeHaveRentedState(
        V2,
        req,
        Set.of(NO_DROP_OFF_ZONE_TIER, NO_DROP_OFF_ZONE_BIRD)
      );

      var states = edge.traverse(haveRentedState);

      // Walking + tier committed + generic (bird not in allowed list)
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
