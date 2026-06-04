package org.opentripplanner.service.vehiclerental.street.geofencing;

import java.util.Set;
import javax.annotation.Nullable;
import org.opentripplanner.service.vehiclerental.model.GeofencingZone;
import org.opentripplanner.street.search.TraverseMode;
import org.opentripplanner.street.search.state.State;
import org.opentripplanner.street.search.state.VehicleRentalState;

/**
 * Enforcement for restricted geofencing zones (no-drop-off and no-traversal). Logic varies by
 * search direction and zone restriction type.
 *
 * <h3>Overrides</h3>
 * <ul>
 *   <li>{@link #forwardApproachingEntry} — entering a restricted zone in forward search:
 *       <ul>
 *         <li>no-traversal: fork — drop here, or ride into the zone (ride branch will be killed
 *             at the next edge by {@link #enforceInside}).</li>
 *         <li>no-drop-off: fork — drop here, or ride through. Post-traversal veto removes the
 *             drop branch if the edge entered a no-drop-off zone during traversal.</li>
 *       </ul></li>
 *   <li>{@link #forwardApproachingExit} — block immediately at the inside-side boundary vertex
 *       of a no-traversal zone (catches the ride branch from
 *       {@link #forwardApproachingEntry} the instant it lands inside).</li>
 *   <li>{@link #enforceInside} — defense in depth: block any renting state currently inside a
 *       no-traversal zone. Catches pickups inside the zone and any placement that bypassed the
 *       boundary methods.</li>
 *   <li>{@link #arriveByApproaching} — block committed renting states from entering a
 *       no-traversal zone in reverse-time.</li>
 *   <li>{@link #arriveByAtBoundary} — HAVE_RENTED walker only; produce a walking branch.
 *       Renting branches are deferred by {@link DeferredForkHandler}.</li>
 * </ul>
 *
 * <h3>Inherited as no-op (default {@code null})</h3>
 * <ul>
 *   <li>{@code forwardCrossingExit} — exiting a restricted zone in forward is fine; the
 *       restriction is on being <em>inside</em>.</li>
 *   <li>{@code forwardCrossingEntry} — entering decision was made one edge earlier via
 *       {@link #forwardApproachingEntry}.</li>
 * </ul>
 *
 * <p>Station rentals can't legally drop mid-street; they're blocked outright when approaching
 * a no-traversal zone.
 */
final class RestrictedZoneEnforcement implements GeofencingEnforcement {

  static final RestrictedZoneEnforcement INSTANCE = new RestrictedZoneEnforcement();

  private RestrictedZoneEnforcement() {}

  /**
   * Forward search: the next edge from tov will enter the restricted zone. The rider must
   * decide before entering — drop here (and walk on) or continue riding into the zone.
   *
   * <p>Behavior depends on zone restriction and rental type:
   * <ul>
   *   <li><b>No-traversal, floating</b> — fork: drop branch + ride branch. The ride branch
   *       will be killed at the next edge by {@link #enforceInside} once the state lands
   *       inside.</li>
   *   <li><b>No-drop-off, floating</b> — fork: drop branch + ride branch. The drop branch is
   *       post-veto'd if the edge itself crossed into a no-drop-off zone.</li>
   *   <li><b>No-traversal, station</b> — block (station rentals can't drop mid-street).</li>
   *   <li><b>No-drop-off, station</b> — pass through (mid-street drops are irrelevant to
   *       station rentals; they only drop at stations).</li>
   * </ul>
   *
   * <p>Returns:
   * <ul>
   *   <li>{@code null} — non-renting state; or station rental into a no-drop-off zone.</li>
   *   <li>{@code State.empty()} — station rental into no-traversal zone; or no-traversal fork
   *       where the drop position is also banned (overlapping no-drop-off zone — dead end).</li>
   *   <li>{@code State[2]} — both fork branches (drop + ride) survived.</li>
   *   <li>{@code State[1]} — only one branch survived (e.g., post-traversal veto removed the
   *       drop branch in the no-drop-off case, or the ride branch traversal failed).</li>
   *   <li>{@code State[0]} — both branches failed to traverse (edge unreachable).</li>
   * </ul>
   */
  @Override
  @Nullable
  public State[] forwardApproachingEntry(GeofencingZone zone, State state, EdgeTraversal edge) {
    if (!state.isRentingVehicle()) {
      return null;
    }
    // Station rentals can't legally drop mid-street, so the floating-style fork doesn't apply.
    if (state.isRentingVehicleFromStation()) {
      if (Boolean.TRUE.equals(zone.traversalBanned())) {
        return State.empty();
      }
      return null;
    }
    if (Boolean.TRUE.equals(zone.traversalBanned())) {
      return forwardEnteringNoTraversal(state, edge);
    }
    if (Boolean.TRUE.equals(zone.dropOffBanned())) {
      return forwardEnteringNoDropOff(state, edge);
    }
    return null;
  }

  /**
   * Forward search: tov is the inside-side boundary vertex of a no-traversal zone. Block the
   * renting state immediately when crossing onto an inside vertex of a no-traversal zone, before
   * {@link #enforceInside} would catch it on the next edge.
   *
   * <p>Returns:
   * <ul>
   *   <li>{@code null} — zone isn't no-traversal (no-drop-off doesn't block here).</li>
   *   <li>{@code State.empty()} — no-traversal zone. Block.</li>
   * </ul>
   */
  @Override
  @Nullable
  public State[] forwardApproachingExit(GeofencingZone zone, State state, EdgeTraversal edge) {
    if (Boolean.TRUE.equals(zone.traversalBanned())) {
      return State.empty();
    }
    return null;
  }

  /**
   * Set-level invariant: a renting state cannot be inside a no-traversal zone. The check uses
   * {@link State#isTraversalBannedByCurrentZones()} which resolves the field across the state's
   * current zones with per-network priority precedence — a higher-priority overlapping zone
   * with {@code traversalBanned=false} correctly overrides a lower-priority {@code true}.
   *
   * <p>Catches: pickups inside a no-traversal zone, the ride branch from
   * {@link #forwardApproachingEntry} once it lands inside (defense in depth — usually caught at
   * the inside-side vertex by {@link #forwardApproachingExit}), and any other anomalous
   * placement.
   *
   * <p>Returns:
   * <ul>
   *   <li>{@code null} — not renting, or no traversal-banned zone resolves true for the state.</li>
   *   <li>{@code State.empty()} — renting state inside a (resolved) no-traversal zone. Block.</li>
   * </ul>
   */
  @Override
  @Nullable
  public State[] enforceInside(Set<GeofencingZone> currentZones, State state, EdgeTraversal edge) {
    if (state.isRentingVehicle() && state.isTraversalBannedByCurrentZones()) {
      return State.empty();
    }
    return null;
  }

  /**
   * ArriveBy search: a committed renting state (network-bound, mid-rental) would, in real time,
   * be riding <em>into</em> this zone on the current edge. For a no-traversal zone, that's
   * illegal — the bike couldn't have legally crossed this boundary. Block.
   *
   * <p>Only committed states are checked here. HAVE_RENTED walkers don't trigger this — they're
   * handled by {@link #arriveByAtBoundary}. Generic (null-network) renting states aren't yet
   * committed and are handled separately by {@link NetworkCommitmentHandler}.
   *
   * <p>Returns:
   * <ul>
   *   <li>{@code null} — not a committed renting state (HAVE_RENTED, generic null-network, or
   *       non-renting); or zone isn't no-traversal (no-drop-off doesn't block entry).</li>
   *   <li>{@code State.empty()} — committed renting state about to enter a no-traversal zone
   *       in real time. Block.</li>
   * </ul>
   */
  @Override
  @Nullable
  public State[] arriveByApproaching(GeofencingZone zone, State state, EdgeTraversal edge) {
    if (
      state.isRentingVehicle() &&
      state.getVehicleRentalNetwork() != null &&
      Boolean.TRUE.equals(zone.traversalBanned())
    ) {
      return State.empty();
    }
    return null;
  }

  /**
   * ArriveBy search: a HAVE_RENTED walker is at a boundary where, in real time, the walker
   * exits the restricted zone (i.e., the rider dropped just outside the zone and was walking
   * through it back to destination). Produce a walking continuation; renting branches are
   * deferred to the next edge by {@link DeferredForkHandler}.
   *
   * <p>Returns:
   * <ul>
   *   <li>{@code null} — state isn't a HAVE_RENTED walker.</li>
   *   <li>{@code State.empty()} — walking traversal failed (edge not walkable).</li>
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
   * Helper for {@link #forwardApproachingEntry} on a no-traversal zone: fork — drop vehicle +
   * continue riding. The ride branch will be blocked at the next edge by the pre-guard.
   */
  private State[] forwardEnteringNoTraversal(State state, EdgeTraversal edge) {
    if (state.isDropOffBannedByCurrentZones()) {
      return State.empty();
    }

    var dropEditor = edge.traverse(state, state.currentMode());
    State dropState = null;
    if (dropEditor != null) {
      dropEditor.dropFloatingVehicle(
        state.vehicleRentalFormFactor(),
        state.rentalVehiclePropulsionType(),
        state.getVehicleRentalNetwork(),
        false
      );
      dropState = dropEditor.makeState();
    }

    var rideEditor = edge.traverse(state, state.currentMode());
    State rideState = rideEditor != null ? rideEditor.makeState() : null;

    return State.ofNullable(dropState, rideState);
  }

  /**
   * Helper for {@link #forwardApproachingEntry} on a no-drop-off zone: fork — drop vehicle +
   * continue riding. If already inside a restricted zone, just pass through (can't drop here
   * anyway). Post-traversal veto: if the traversal entered a no-drop-off zone during the edge,
   * discard the drop branch.
   */
  @Nullable
  private State[] forwardEnteringNoDropOff(State state, EdgeTraversal edge) {
    if (state.isDropOffBannedByCurrentZones()) {
      return null;
    }

    var dropEditor = edge.traverse(state, state.currentMode());
    State dropState = null;
    if (dropEditor != null) {
      if (!dropEditor.isDropOffBannedByCurrentZones()) {
        dropEditor.dropFloatingVehicle(
          state.vehicleRentalFormFactor(),
          state.rentalVehiclePropulsionType(),
          state.getVehicleRentalNetwork(),
          false
        );
        dropState = dropEditor.makeState();
      }
    }

    var rideEditor = edge.traverse(state, state.currentMode());
    State rideState = rideEditor != null ? rideEditor.makeState() : null;

    return State.ofNullable(dropState, rideState);
  }
}
