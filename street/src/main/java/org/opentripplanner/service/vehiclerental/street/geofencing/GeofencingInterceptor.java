package org.opentripplanner.service.vehiclerental.street.geofencing;

import java.util.List;
import javax.annotation.Nullable;
import org.opentripplanner.street.search.TraverseMode;
import org.opentripplanner.street.search.state.State;

/**
 * Intercepts rental edge traversals to enforce geofencing zone restrictions. Called from
 * {@code StreetEdge.traverse()} — returns {@code State[]} to override the normal traversal,
 * or {@code null} to let it proceed.
 *
 * <p>Orchestrator only. Per-zone decisions live in {@link GeofencingEnforcement}
 * implementations. The interceptor:
 * <ul>
 *   <li>Runs a pre-guard for renting states inside a no-traversal zone (force drop or block).</li>
 *   <li>Iterates boundaries and dispatches to the strategy by <em>position</em>:
 *       approaching (tov, outside), at far boundary (tov, inside), at near boundary
 *       (fromv, inside).</li>
 *   <li>Delegates deferred renting forks to {@link DeferredForkHandler} and generic
 *       network commitment to {@link NetworkCommitmentHandler}.</li>
 * </ul>
 */
public class GeofencingInterceptor {

  private GeofencingInterceptor() {}

  @Nullable
  public static State[] apply(
    State s0,
    List<GeofencingBoundaryExtension> fromBoundaries,
    List<GeofencingBoundaryExtension> toBoundaries,
    EdgeTraversal edge
  ) {
    // Pre-guard: renting states inside no-traversal zones must drop or be blocked.
    // Station rentals can't legally drop mid-street, so they're blocked outright.
    if (s0.isRentingVehicle() && s0.isTraversalBannedByCurrentZones()) {
      if (s0.isRentingVehicleFromStation() || s0.isDropOffBannedByCurrentZones()) {
        return State.empty();
      }
      return forceDrop(s0, edge);
    }

    if (s0.getRequest().arriveBy()) {
      return evaluateArriveBy(s0, fromBoundaries, toBoundaries, edge);
    } else {
      return evaluateForward(s0, fromBoundaries, toBoundaries, edge);
    }
  }

  @Nullable
  private static State[] evaluateForward(
    State s0,
    List<GeofencingBoundaryExtension> fromBoundaries,
    List<GeofencingBoundaryExtension> toBoundaries,
    EdgeTraversal edge
  ) {
    if (!s0.isRentingVehicle()) {
      return null;
    }
    String network = s0.getVehicleRentalNetwork();

    // tov boundaries: state will arrive at tov on this edge. entering=true means tov is outside
    // (next edge enters the zone — fork at approach). entering=false means tov is inside (next
    // edge exits — at far boundary).
    for (var boundary : toBoundaries) {
      var zone = boundary.zone();
      if (network != null && !zone.id().getFeedId().equals(network)) {
        continue;
      }
      var enforcement = GeofencingEnforcement.forZone(zone);
      var result = boundary.entering()
        ? enforcement.forwardApproachingEntry(zone, s0, edge)
        : enforcement.forwardApproachingExit(zone, s0, edge);
      if (result != null) {
        return result;
      }
    }

    // fromv boundaries: state at fromv is at the boundary, current edge crosses it.
    // entering=false → fromv inside, edge exits the zone (CrossingExit, BA fallback drop).
    // entering=true  → fromv outside, edge enters the zone (CrossingEntry, normally a no-op
    // since the fork was made one edge earlier by ApproachingEntry).
    for (var boundary : fromBoundaries) {
      var zone = boundary.zone();
      if (network != null && !zone.id().getFeedId().equals(network)) {
        continue;
      }
      var enforcement = GeofencingEnforcement.forZone(zone);
      var result = boundary.entering()
        ? enforcement.forwardCrossingEntry(zone, s0, edge)
        : enforcement.forwardCrossingExit(zone, s0, edge);
      if (result != null) {
        return result;
      }
    }

    return null;
  }

  @Nullable
  private static State[] evaluateArriveBy(
    State s0,
    List<GeofencingBoundaryExtension> fromBoundaries,
    List<GeofencingBoundaryExtension> toBoundaries,
    EdgeTraversal edge
  ) {
    var deferredResult = DeferredForkHandler.applyDeferredFork(s0, edge);
    if (deferredResult != null) {
      return deferredResult;
    }

    if (fromBoundaries.isEmpty()) {
      return null;
    }

    String network = s0.getVehicleRentalNetwork();

    // Committed renting: when the real-time edge would enter the zone (fromv + entering=false),
    // the committed state can't ride into a no-traversal zone — block.
    if (s0.isRentingVehicle() && network != null) {
      for (var boundary : fromBoundaries) {
        var zone = boundary.zone();
        if (!zone.id().getFeedId().equals(network)) {
          continue;
        }
        if (boundary.entering()) {
          continue;
        }
        var enforcement = GeofencingEnforcement.forZone(zone);
        var result = enforcement.arriveByApproaching(zone, s0, edge);
        if (result != null) {
          return result;
        }
      }
    }

    var walkerResult = WalkerBoundaryHandler.apply(s0, fromBoundaries, toBoundaries, edge);
    if (walkerResult != null) {
      return walkerResult;
    }

    // Generic states: network commitment at zone boundaries
    if (s0.isRentingFloatingVehicle() && network == null) {
      return NetworkCommitmentHandler.applyNetworkCommitment(s0, fromBoundaries, edge);
    }

    return null;
  }

  private static State[] forceDrop(State s0, EdgeTraversal edge) {
    var editor = edge.traverse(s0, TraverseMode.WALK);
    if (editor != null) {
      if (editor.isDropOffBannedByCurrentZones()) {
        return State.empty();
      }
      editor.dropFloatingVehicle(
        s0.vehicleRentalFormFactor(),
        s0.rentalVehiclePropulsionType(),
        s0.getVehicleRentalNetwork(),
        s0.getRequest().arriveBy()
      );
      return State.ofNullable(editor.makeState());
    }
    return State.empty();
  }
}
