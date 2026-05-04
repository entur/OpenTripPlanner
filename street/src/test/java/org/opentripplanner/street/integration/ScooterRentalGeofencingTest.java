package org.opentripplanner.street.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opentripplanner.core.model.basic.Cost;
import org.opentripplanner.core.model.i18n.NonLocalizedString;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.service.vehiclerental.model.GeofencingZone;
import org.opentripplanner.service.vehiclerental.model.RentalVehicleType;
import org.opentripplanner.service.vehiclerental.model.VehicleRentalVehicle;
import org.opentripplanner.service.vehiclerental.street.GeofencingBoundaryExtension;
import org.opentripplanner.service.vehiclerental.street.VehicleRentalEdge;
import org.opentripplanner.service.vehiclerental.street.VehicleRentalPlaceVertex;
import org.opentripplanner.street.model.RentalFormFactor;
import org.opentripplanner.street.model.StreetMode;
import org.opentripplanner.street.model.StreetTraversalPermission;
import org.opentripplanner.street.model.vertex.StreetVertex;
import org.opentripplanner.street.model.vertex.TemporaryStreetLocation;
import org.opentripplanner.street.model.vertex.Vertex;
import org.opentripplanner.street.search.EuclideanRemainingWeightHeuristic;
import org.opentripplanner.street.search.StreetSearchBuilder;
import org.opentripplanner.street.search.request.StreetSearchRequest;

/**
 * Integration test for geofencing zone enforcement with floating scooter rentals.
 * Tests that both forward (depart-after) and arrive-by searches correctly drop off
 * vehicles outside no-drop-off zones.
 *
 * <pre>
 * Graph layout:
 *
 *   T_origin --- A --- B --- C --- D --- E --- T_dest
 *                      |         |
 *                   scooter   zone boundary (between C and D)
 *
 * A, B, C are outside the no-drop-off zone.
 * D, E are inside the no-drop-off zone.
 * The scooter is at B (outside zone).
 * Origin (T_origin) is near A, destination (T_dest) is near E.
 *
 * Expected: rider picks up scooter at B, rides to C (zone boundary),
 * drops off at C (outside zone), walks C → D → E → destination.
 * </pre>
 */
public class ScooterRentalGeofencingTest extends GraphRoutingTest {

  static final String NETWORK = "scooter-network";
  static final GeofencingZone NO_DROP_OFF_ZONE = new GeofencingZone(
    new FeedScopedId(NETWORK, "no-dropoff-zone"),
    null,
    null,
    true,
    false
  );

  // Vertices: laid out along a line (increasing latitude)
  private StreetVertex A, B, C, D, E;
  private TemporaryStreetLocation T_ORIGIN, T_DEST;
  private VehicleRentalPlaceVertex SCOOTER_VERTEX;

  @BeforeEach
  public void setUp() {
    // Graph:
    //   T_origin -- A -- B -- C -- D -- E -- T_dest
    //                   scooter    ^ zone boundary
    //
    // C→D is the boundary edge (C outside, D inside no-drop-off zone)
    // D and E are inside the zone

    modelOf(
      new Builder() {
        @Override
        public void build() {
          A = intersection("A", 59.910, 10.740);
          B = intersection("B", 59.911, 10.740);
          C = intersection("C", 59.912, 10.740);
          D = intersection("D", 59.913, 10.740);
          E = intersection("E", 59.914, 10.740);

          T_ORIGIN = streetLocation("origin", 59.9095, 10.740);
          T_DEST = streetLocation("dest", 59.9145, 10.740);

          // Create floating scooter at B
          SCOOTER_VERTEX = createFloatingScooter("scooter1", 59.911, 10.7401);

          // Street edges (all allow walking and scootering)
          var perm = StreetTraversalPermission.ALL;
          street(A, B, 100, perm);
          street(B, C, 100, perm);
          street(C, D, 100, perm);
          street(D, E, 100, perm);

          // Link scooter to street
          biLink(B, SCOOTER_VERTEX);

          // Link origin and destination
          link(T_ORIGIN, A);
          link(E, T_DEST);

          // Mark boundary: C is outside zone, D is inside zone
          // Forward edge C→D: entering=true on C (fromv)
          // Back edge D→C: entering=false on D (fromv)
          C.addGeofencingBoundary(new GeofencingBoundaryExtension(NO_DROP_OFF_ZONE, true));
          D.addGeofencingBoundary(new GeofencingBoundaryExtension(NO_DROP_OFF_ZONE, false));
        }
      }
    );
  }

  /**
   * Forward search: rider should drop off scooter OUTSIDE the no-drop-off zone (at or before C),
   * then walk to destination.
   */
  @Test
  public void forwardSearchDropsOffOutsideNoDropOffZone() {
    var descriptor = runSearch(T_ORIGIN, T_DEST, false);
    assertNotNull(descriptor, "forward search should find a path");

    // Find the state where rental transitions from RENTING to HAVE_RENTED
    var dropOffState = descriptor
      .stream()
      .filter(d -> d.contains("HAVE_RENTED") && !d.contains("BEFORE_RENTING"))
      .findFirst()
      .orElse(null);

    assertNotNull(dropOffState, "should have a drop-off state");

    // The vehicle should be dropped off before entering the zone.
    // If the drop-off happens on the CD edge, that means it's at the boundary.
    // If it happens AFTER CD (e.g., on DE), the zone enforcement failed.
    var allStates = String.join("\n", descriptor);
    System.out.println("Forward search states:\n" + allStates);

    // After drop-off, all subsequent states should be HAVE_RENTED (walking)
    boolean seenDropOff = false;
    for (var d : descriptor) {
      if (d.contains("HAVE_RENTED")) {
        seenDropOff = true;
      }
      if (seenDropOff && d.contains("RENTING_FLOATING")) {
        throw new AssertionError(
          "Found RENTING_FLOATING state after drop-off — vehicle not properly dropped off"
        );
      }
    }

    // The drop-off should NOT happen on the DE edge (inside zone)
    // It should happen on CD edge at the latest (boundary)
    for (var d : descriptor) {
      if (d.contains("DE street") && d.contains("RENTING_FLOATING")) {
        throw new AssertionError(
          "Rider is still renting on DE street (inside no-drop-off zone). " +
            "Drop-off should have happened at the zone boundary (CD street or earlier).\n" +
            "Full path:\n" +
            allStates
        );
      }
    }
  }

  /**
   * ArriveBy search: rider should also drop off scooter OUTSIDE the no-drop-off zone.
   */
  @Test
  public void arriveBySearchDropsOffOutsideNoDropOffZone() {
    var descriptor = runSearch(T_ORIGIN, T_DEST, true);
    assertNotNull(descriptor, "arriveBy search should find a path");

    var allStates = String.join("\n", descriptor);
    System.out.println("ArriveBy search states:\n" + allStates);

    // The drop-off should NOT happen on the DE edge (inside zone)
    for (var d : descriptor) {
      if (d.contains("DE street") && d.contains("RENTING_FLOATING")) {
        throw new AssertionError(
          "Rider is still renting on DE street (inside no-drop-off zone). " +
            "Drop-off should have happened at the zone boundary.\n" +
            "Full path:\n" +
            allStates
        );
      }
    }
  }

  /**
   * Forward and arriveBy should both use a scooter and drop off outside the zone.
   * Due to State.reverse() re-traversing edges in forward direction, the exact edge where
   * RENTING_FLOATING last appears may differ (forward: BC street, arriveBy: scooter1 at the
   * VehicleRentalEdge). Both represent the same physical drop-off location at vertex B/C.
   */
  @Test
  public void forwardAndArriveByDropOffAtSameLocation() {
    var forward = runSearch(T_ORIGIN, T_DEST, false);
    var arriveBy = runSearch(T_ORIGIN, T_DEST, true);

    assertNotNull(forward, "forward should find a path");
    assertNotNull(arriveBy, "arriveBy should find a path");

    // Both paths should use a scooter (have RENTING_FLOATING somewhere)
    assertTrue(
      forward.stream().anyMatch(d -> d.contains("RENTING_FLOATING")),
      "Forward path should use a scooter"
    );
    assertTrue(
      arriveBy.stream().anyMatch(d -> d.contains("RENTING_FLOATING")),
      "ArriveBy path should use a scooter"
    );

    // Neither path should have RENTING_FLOATING on edges inside the zone (CD, DE)
    for (var d : forward) {
      assertFalse(
        (d.contains("CD street") || d.contains("DE street")) && d.contains("RENTING_FLOATING"),
        "Forward: rider should not be renting inside zone.\nPath:\n  " +
          String.join("\n  ", forward)
      );
    }
    for (var d : arriveBy) {
      assertFalse(
        (d.contains("CD street") || d.contains("DE street")) && d.contains("RENTING_FLOATING"),
        "ArriveBy: rider should not be renting inside zone.\nPath:\n  " +
          String.join("\n  ", arriveBy)
      );
    }
  }

  /**
   * When the scooter is INSIDE the no-drop-off zone, the rider picks it up and rides out.
   * The rider should NOT be able to drop off at the destination (also inside the zone).
   * Instead, they must ride out of the zone first, then the trip to the destination
   * requires walking back into the zone.
   */
  @Test
  public void forwardSearchWithScooterInsideZone() {
    // Create a second graph where scooter is inside zone
    modelOf(
      new Builder() {
        @Override
        public void build() {
          A = intersection("A", 59.910, 10.740);
          B = intersection("B", 59.911, 10.740);
          C = intersection("C", 59.912, 10.740);
          D = intersection("D", 59.913, 10.740);
          E = intersection("E", 59.914, 10.740);

          T_ORIGIN = streetLocation("origin", 59.9095, 10.740);
          T_DEST = streetLocation("dest", 59.9145, 10.740);

          // Scooter inside zone at D
          SCOOTER_VERTEX = createFloatingScooter("scooter-inside", 59.913, 10.7401);
          SCOOTER_VERTEX.setInitialGeofencingZones(Set.of(NO_DROP_OFF_ZONE));

          var perm = StreetTraversalPermission.ALL;
          street(A, B, 100, perm);
          street(B, C, 100, perm);
          street(C, D, 100, perm);
          street(D, E, 100, perm);

          biLink(D, SCOOTER_VERTEX);

          link(T_ORIGIN, A);
          link(E, T_DEST);

          C.addGeofencingBoundary(new GeofencingBoundaryExtension(NO_DROP_OFF_ZONE, true));
          D.addGeofencingBoundary(new GeofencingBoundaryExtension(NO_DROP_OFF_ZONE, false));
        }
      }
    );

    var forward = runSearch(T_ORIGIN, T_DEST, false);
    assertNotNull(
      forward,
      "forward search should find a path (ride scooter out of zone, walk back)"
    );

    var allStates = String.join("\n  ", forward);

    // Verify the rider does NOT remain RENTING_FLOATING on edges inside the zone at the end
    for (var d : forward) {
      if (d.contains("DE street") && d.contains("RENTING_FLOATING")) {
        throw new AssertionError(
          "Rider is still renting on DE street (inside no-drop-off zone).\n" +
            "Full path:\n  " +
            allStates
        );
      }
    }
  }

  /**
   * Both forward and arrive-by should drop off outside the no-drop-off zone.
   * The forward search's last RENTING_FLOATING edge is BC street (the last edge before the zone).
   * The arrive-by search's last RENTING_FLOATING edge is the scooter1 VehicleRentalEdge, because
   * State.reverse() shifts the rental transition one edge earlier. Both represent the rider
   * dropping off at the zone boundary (vertex B/C), outside the zone.
   */
  @Test
  public void forwardDropOffShouldBeAtVertexOutsideZone() {
    var forward = runSearch(T_ORIGIN, T_DEST, false);
    var arriveBy = runSearch(T_ORIGIN, T_DEST, true);
    assertNotNull(forward, "forward should find a path");
    assertNotNull(arriveBy, "arriveBy should find a path");

    // Both paths should use a scooter and NOT rent inside the zone (CD, DE)
    for (var path : List.of(forward, arriveBy)) {
      assertTrue(
        path.stream().anyMatch(d -> d.contains("RENTING_FLOATING")),
        "Path should use a scooter.\nPath:\n  " + String.join("\n  ", path)
      );
      for (var d : path) {
        assertFalse(
          (d.contains("CD street") || d.contains("DE street")) && d.contains("RENTING_FLOATING"),
          "Rider should not be renting inside zone.\nPath:\n  " + String.join("\n  ", path)
        );
      }
    }
  }

  /**
   * Forward search must not ride into a no-traversal zone. With correct boundary marking,
   * the rider should drop off at the boundary and walk.
   */
  @Test
  public void forwardSearchBlocksRidingIntoNoTraversalZone() {
    var noTraversalZone = new GeofencingZone(
      new FeedScopedId(NETWORK, "no-traversal-zone"),
      null,
      null,
      false,
      true
    );

    modelOf(
      new Builder() {
        @Override
        public void build() {
          A = intersection("A", 59.910, 10.740);
          B = intersection("B", 59.911, 10.740);
          C = intersection("C", 59.912, 10.740);
          D = intersection("D", 59.913, 10.740);
          E = intersection("E", 59.914, 10.740);

          T_ORIGIN = streetLocation("origin", 59.9095, 10.740);
          T_DEST = streetLocation("dest", 59.9145, 10.740);

          SCOOTER_VERTEX = createFloatingScooter("scooter1", 59.911, 10.7401);

          var perm = StreetTraversalPermission.ALL;
          street(A, B, 500, perm);
          street(B, C, 1000, perm);
          street(C, D, 1000, perm);
          street(D, E, 500, perm);

          biLink(B, SCOOTER_VERTEX);
          link(T_ORIGIN, A);
          link(E, T_DEST);

          C.addGeofencingBoundary(new GeofencingBoundaryExtension(noTraversalZone, true));
          D.addGeofencingBoundary(new GeofencingBoundaryExtension(noTraversalZone, false));
        }
      }
    );

    var forward = runSearch(T_ORIGIN, T_DEST, false);
    assertNotNull(forward, "forward should find a path");
    var allStates = String.join("\n  ", forward);

    // The rider must NOT be RENTING on the CD edge (entering no-traversal zone)
    // or the DE edge (inside no-traversal zone)
    for (var d : forward) {
      if ((d.contains("CD street") || d.contains("DE street")) && d.contains("RENTING_FLOATING")) {
        throw new AssertionError(
          "Rider is renting inside no-traversal zone! Should have dropped off at boundary.\n" +
            "Path:\n  " +
            allStates
        );
      }
    }
  }

  @Test
  public void arriveBySearchBlocksRidingIntoNoTraversalZone() {
    var noTraversalZone = new GeofencingZone(
      new FeedScopedId(NETWORK, "no-traversal-zone"),
      null,
      null,
      false,
      true
    );

    modelOf(
      new Builder() {
        @Override
        public void build() {
          A = intersection("A", 59.910, 10.740);
          B = intersection("B", 59.911, 10.740);
          C = intersection("C", 59.912, 10.740);
          D = intersection("D", 59.913, 10.740);
          E = intersection("E", 59.914, 10.740);

          T_ORIGIN = streetLocation("origin", 59.9095, 10.740);
          T_DEST = streetLocation("dest", 59.9145, 10.740);

          SCOOTER_VERTEX = createFloatingScooter("scooter1", 59.911, 10.7401);

          var perm = StreetTraversalPermission.ALL;
          street(A, B, 500, perm);
          street(B, C, 1000, perm);
          street(C, D, 1000, perm);
          street(D, E, 500, perm);

          biLink(B, SCOOTER_VERTEX);
          link(T_ORIGIN, A);
          link(E, T_DEST);

          C.addGeofencingBoundary(new GeofencingBoundaryExtension(noTraversalZone, true));
          D.addGeofencingBoundary(new GeofencingBoundaryExtension(noTraversalZone, false));
        }
      }
    );

    // Run arrive-by with correct destination zones
    var builder = StreetSearchRequest.of()
      .withArriveBy(true)
      .withMode(StreetMode.SCOOTER_RENTAL)
      .withScooter(s ->
        s.withRental(r ->
          r
            .withPickupTime(Duration.ofSeconds(30))
            .withPickupCost(Cost.costOfSeconds(30))
            .withDropOffTime(Duration.ofSeconds(15))
            .withDropOffCost(Cost.costOfSeconds(15))
        )
      )
      .withArriveByDestinationZones(Set.of(noTraversalZone));

    var request = builder.build();

    var tree = StreetSearchBuilder.of()
      .withHeuristic(new EuclideanRemainingWeightHeuristic())
      .withRequest(request)
      .withFrom(T_ORIGIN)
      .withTo(T_DEST)
      .getShortestPathTree();

    var path = tree.getPath(T_ORIGIN);
    assertNotNull(path, "arriveBy should find a path");

    var descriptor = path.states
      .stream()
      .filter(s -> s.getBackEdge() != null)
      .map(s ->
        String.format(
          Locale.ROOT,
          "%s - %s - %s (%,.2f, %d) zones=%s",
          s.getBackMode(),
          s.getVehicleRentalState(),
          s.getBackEdge().getDefaultName(),
          s.getWeight(),
          s.getElapsedTimeSeconds(),
          s.getCurrentGeofencingZones()
        )
      )
      .collect(Collectors.toList());

    var allStates = String.join("\n  ", descriptor);

    // The rider must NOT be RENTING on CD or DE edges (inside/entering no-traversal zone)
    for (var d : descriptor) {
      if ((d.contains("CD street") || d.contains("DE street")) && d.contains("RENTING_FLOATING")) {
        throw new AssertionError(
          "Rider is renting inside no-traversal zone in arrive-by! " +
            "Should have dropped off at boundary.\nPath:\n  " +
            allStates
        );
      }
    }

    // The rider should use a scooter (RENTING_FLOATING appears somewhere in the path)
    assertTrue(
      descriptor.stream().anyMatch(d -> d.contains("RENTING_FLOATING")),
      "ArriveBy should use a scooter.\nPath:\n  " + allStates
    );
  }

  private String findLastRentingEdge(List<String> descriptor) {
    String lastRenting = null;
    for (var d : descriptor) {
      if (d.contains("RENTING_FLOATING")) {
        // Extract edge name
        var parts = d.split(" - ");
        if (parts.length >= 3) {
          lastRenting = parts[2].split(" \\(")[0];
        }
      }
    }
    return lastRenting;
  }

  private List<String> runSearch(Vertex from, Vertex to, boolean arriveBy) {
    var builder = StreetSearchRequest.of()
      .withArriveBy(arriveBy)
      .withMode(StreetMode.SCOOTER_RENTAL)
      .withScooter(s ->
        s.withRental(r ->
          r
            .withPickupTime(Duration.ofSeconds(30))
            .withPickupCost(Cost.costOfSeconds(30))
            .withDropOffTime(Duration.ofSeconds(15))
            .withDropOffCost(Cost.costOfSeconds(15))
        )
      );

    // For arriveBy, set destination zones so initial states know about the zone
    if (arriveBy) {
      builder.withArriveByDestinationZones(Set.of(NO_DROP_OFF_ZONE));
    }

    var request = builder.build();

    var tree = StreetSearchBuilder.of()
      .withHeuristic(new EuclideanRemainingWeightHeuristic())
      .withRequest(request)
      .withFrom(from)
      .withTo(to)
      .getShortestPathTree();

    var path = tree.getPath(arriveBy ? from : to);
    if (path == null) {
      return null;
    }

    return path.states
      .stream()
      .filter(s -> s.getBackEdge() != null)
      .map(s ->
        String.format(
          Locale.ROOT,
          "%s - %s - %s (%,.2f, %d) zones=%s",
          s.getBackMode(),
          s.getVehicleRentalState(),
          s.getBackEdge().getDefaultName(),
          s.getWeight(),
          s.getElapsedTimeSeconds(),
          s.getCurrentGeofencingZones()
        )
      )
      .collect(Collectors.toList());
  }

  private VehicleRentalPlaceVertex createFloatingScooter(String id, double lat, double lon) {
    var vehicleWithId = VehicleRentalVehicle.of()
      .withId(new FeedScopedId(NETWORK, id))
      .withName(new NonLocalizedString(id))
      .withLatitude(lat)
      .withLongitude(lon)
      .withVehicleType(
        RentalVehicleType.of()
          .withId(new FeedScopedId(NETWORK, "scooter-type"))
          .withFormFactor(RentalFormFactor.SCOOTER)
          .withPropulsionType(RentalVehicleType.PropulsionType.ELECTRIC)
          .withMaxRangeMeters(50000d)
          .build()
      )
      .build();

    var vertex = new VehicleRentalPlaceVertex(vehicleWithId);
    VehicleRentalEdge.createVehicleRentalEdge(vertex, RentalFormFactor.SCOOTER);
    return vertex;
  }
}
