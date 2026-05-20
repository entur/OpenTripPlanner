package org.opentripplanner.service.vehiclerental.street.geofencing;

import java.util.List;
import javax.annotation.Nullable;
import org.opentripplanner.street.search.state.State;
import org.opentripplanner.street.search.state.VehicleRentalState;

/**
 * Handles HAVE_RENTED walkers crossing zone boundaries during arriveBy search. The walker
 * already dropped the vehicle (in real time) and is walking from drop point back to
 * destination; in arriveBy search direction, the walker is going the opposite way.
 *
 * <p>At a paired boundary, this handler produces a walking continuation by dispatching to
 * {@link GeofencingEnforcement#arriveByAtBoundary}. Renting branches for "the rental could
 * have been picked up here" are not produced here — they're deferred to the next edge by
 * {@link DeferredForkHandler}.
 *
 * <p>The direction that triggers the walker handler is zone-type-specific:
 * <ul>
 *   <li><b>Business area</b> — drop must happen inside the BA, so the walker exited the BA in
 *       real time. Trigger when fromv has entering=false (real-time edge exits BA).</li>
 *   <li><b>Restricted zone</b> — drop must happen outside the zone, so the walker exited the
 *       zone in real time. Trigger when fromv has entering=true (real-time edge exits the
 *       restricted zone).</li>
 * </ul>
 *
 * <p>The inverted directions reflect the inverted "inside means rental territory" semantics of
 * the two zone types.
 */
public class WalkerBoundaryHandler {

  private WalkerBoundaryHandler() {}

  /**
   * Dispatches HAVE_RENTED walker enforcement at paired zone boundaries.
   *
   * @return the enforcement result, or {@code null} if no boundary triggered (state isn't a
   *     HAVE_RENTED walker, no paired boundaries, or no boundary direction matched the
   *     walker-exit-in-real-time condition).
   */
  @Nullable
  public static State[] apply(
    State s0,
    List<GeofencingBoundaryExtension> fromBoundaries,
    List<GeofencingBoundaryExtension> toBoundaries,
    EdgeTraversal edge
  ) {
    if (s0.getVehicleRentalState() != VehicleRentalState.HAVE_RENTED) {
      return null;
    }
    for (var boundary : fromBoundaries) {
      if (!hasPairedBoundary(boundary, toBoundaries)) {
        continue;
      }
      var zone = boundary.zone();
      if (!zone.hasRestriction() && !zone.isBusinessArea()) {
        continue;
      }
      boolean walkerExitsInRealTime = zone.isBusinessArea()
        ? !boundary.entering()
        : boundary.entering();
      if (!walkerExitsInRealTime) {
        continue;
      }
      var enforcement = GeofencingEnforcement.forZone(zone);
      var result = enforcement.arriveByAtBoundary(zone, s0, edge);
      if (result != null) {
        return result;
      }
    }
    return null;
  }

  private static boolean hasPairedBoundary(
    GeofencingBoundaryExtension boundary,
    List<GeofencingBoundaryExtension> toBoundaries
  ) {
    for (var tovBoundary : toBoundaries) {
      if (
        tovBoundary.zone().equals(boundary.zone()) && tovBoundary.entering() != boundary.entering()
      ) {
        return true;
      }
    }
    return false;
  }
}
