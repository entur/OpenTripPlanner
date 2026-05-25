package org.opentripplanner.service.vehiclerental.street.geofencing;

import javax.annotation.Nullable;
import org.opentripplanner.service.vehiclerental.model.GeofencingZone;
import org.opentripplanner.street.search.TraverseMode;
import org.opentripplanner.street.search.state.State;
import org.opentripplanner.street.search.state.VehicleRentalState;

/**
 * Enforcement for business area geofencing zones. The logic is inverted compared to restricted
 * zones: exiting the business area is restricted (the rider can't leave the operating area on
 * the vehicle), while entering is allowed.
 *
 * <h3>Overrides</h3>
 * <ul>
 *   <li>{@link #forwardApproachingExit} — ride to tov (last in-BA vertex) and drop there.</li>
 *   <li>{@link #forwardCrossingExit} — force walk past the boundary and drop (fallback for
 *       states that start at the boundary vertex).</li>
 *   <li>{@link #arriveByAtBoundary} — HAVE_RENTED walker only; produce a walking branch.
 *       Renting branches are deferred by {@link DeferredForkHandler}.</li>
 * </ul>
 *
 * <h3>Inherited as no-op (default {@code null})</h3>
 * <ul>
 *   <li>{@code forwardApproachingEntry} — entering a BA is allowed; nothing to enforce.</li>
 *   <li>{@code forwardCrossingEntry} — entering decision (if any) was made one edge earlier.</li>
 *   <li>{@code arriveByApproaching} — entering a BA in reverse-time is allowed.</li>
 * </ul>
 *
 * <p>Station rentals can't legally drop mid-street and are blocked outright at any BA exit.
 * Post-traversal veto: if the edge crosses into a no-drop-off zone, drops are blocked.
 */
final class BusinessAreaEnforcement implements GeofencingEnforcement {

  static final BusinessAreaEnforcement INSTANCE = new BusinessAreaEnforcement();

  private BusinessAreaEnforcement() {}

  /**
   * Forward search: the next edge from tov will exit the BA. The rider must drop the vehicle
   * before leaving the operating area — the drop happens at tov (the last in-BA vertex), in the
   * rider's current riding mode.
   *
   * <p>Returns:
   * <ul>
   *   <li>{@code null} — state isn't renting; nothing to enforce.</li>
   *   <li>{@code State.empty()} — station rental (can't legally drop mid-street); or the drop
   *       position is banned by an overlapping no-drop-off zone (pre- or post-traversal); or the
   *       edge traversal itself failed.</li>
   *   <li>{@code State[1]} — floating rental rode to tov in {@code currentMode} and dropped
   *       successfully.</li>
   * </ul>
   */
  @Override
  @Nullable
  public State[] forwardApproachingExit(GeofencingZone zone, State state, EdgeTraversal edge) {
    if (!state.isRentingVehicle()) {
      return null;
    }
    if (state.isRentingVehicleFromStation()) {
      return State.empty();
    }
    return forwardExit(state, edge, state.currentMode());
  }

  /**
   * Forward search: the current edge crosses outward across the BA boundary (fromv inside, tov
   * outside). Fallback path for states that start at the boundary vertex with no prior edge to
   * fire {@link #forwardApproachingExit}. The rider walks past the boundary and drops at tov.
   *
   * <p>Returns:
   * <ul>
   *   <li>{@code null} — state isn't renting; nothing to enforce.</li>
   *   <li>{@code State.empty()} — station rental; drop position banned by an overlapping
   *       no-drop-off zone (pre- or post-traversal); or edge traversal failed.</li>
   *   <li>{@code State[1]} — walked past the boundary in {@code WALK} mode and dropped.</li>
   * </ul>
   */
  @Override
  @Nullable
  public State[] forwardCrossingExit(GeofencingZone zone, State state, EdgeTraversal edge) {
    if (!state.isRentingVehicle()) {
      return null;
    }
    if (state.isRentingVehicleFromStation()) {
      return State.empty();
    }
    return forwardExit(state, edge, TraverseMode.WALK);
  }

  /**
   * ArriveBy search: a HAVE_RENTED walker is at a boundary where, in real time, the walker
   * exits the BA (i.e., the bike was dropped inside the BA and the walker continued out on
   * foot). Produce a walking continuation; renting branches for "the bike could have been
   * ridden to here" are deferred to the next edge by {@link DeferredForkHandler}.
   *
   * <p>Returns:
   * <ul>
   *   <li>{@code null} — state isn't a HAVE_RENTED walker (e.g., still renting or never
   *       rented); nothing to enforce here.</li>
   *   <li>{@code State.empty()} — walking traversal failed (edge not walkable, etc.).</li>
   *   <li>{@code State[1]} — the HAVE_RENTED walker continues walking.</li>
   * </ul>
   */
  @Override
  @Nullable
  public State[] arriveByAtBoundary(GeofencingZone zone, State state, EdgeTraversal edge) {
    if (state.getVehicleRentalState() != VehicleRentalState.HAVE_RENTED) {
      return null;
    }
    var walking = edge.traverse(state, TraverseMode.WALK);
    if (walking != null) {
      return walking.makeStateArray();
    }
    return State.empty();
  }

  /**
   * Force a floating-vehicle drop at the BA boundary. Shared by {@link #forwardApproachingExit}
   * (ride in {@code currentMode} to the last in-zone vertex) and {@link #forwardCrossingExit}
   * (walk past the boundary). Only the traversal mode differs.
   */
  private State[] forwardExit(State state, EdgeTraversal edge, TraverseMode mode) {
    if (state.isDropOffBannedByCurrentZones()) {
      return State.empty();
    }
    var editor = edge.traverse(state, mode);
    if (editor == null) {
      return State.empty();
    }
    if (editor.isDropOffBannedByCurrentZones()) {
      return State.empty();
    }
    editor.dropFloatingVehicle(
      state.vehicleRentalFormFactor(),
      state.rentalVehiclePropulsionType(),
      state.getVehicleRentalNetwork(),
      false
    );
    return State.ofNullable(editor.makeState());
  }
}
