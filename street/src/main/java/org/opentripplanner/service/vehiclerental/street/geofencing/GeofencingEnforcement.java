package org.opentripplanner.service.vehiclerental.street.geofencing;

import java.util.Set;
import javax.annotation.Nullable;
import org.opentripplanner.service.vehiclerental.model.GeofencingZone;
import org.opentripplanner.street.search.state.State;

/**
 * Strategy for enforcing geofencing zone restrictions during street routing. Each zone type
 * (restricted zone, business area) has its own implementation with zone-type-specific logic
 * for fork, block, and drop decisions.
 *
 * <p>The interface is organized by <em>boundary position</em> — where the state sits relative
 * to the zone boundary on the current edge — and search direction. The orchestrator
 * ({@link GeofencingInterceptor}) determines the position from the boundary marker and
 * dispatches to the matching method. Strategies that have no opinion about a given position
 * return {@code null} (the default).
 *
 * <h3>Positions (in forward / real-time semantics)</h3>
 *
 * <p>A "position" is defined by which end of the current edge carries the boundary marker and
 * which side of the zone that vertex sits on. There are four such combinations; the position
 * name describes <em>what the upcoming or current edge does</em> relative to the zone:
 *
 * <ul>
 *   <li><b>{@code forwardApproachingEntry}</b> — tov is on the <em>outside</em>. The current
 *       edge does not cross the boundary; the next edge from tov will <em>enter</em> the zone.
 *       Fork point for entry decisions.</li>
 *   <li><b>{@code forwardApproachingExit}</b> — tov is on the <em>inside</em>. The current
 *       edge does not cross; the next edge from tov will <em>exit</em>. Drop point for
 *       business areas (drop at tov, still inside).</li>
 *   <li><b>{@code forwardCrossingExit}</b> — fromv is on the <em>inside</em>. The current
 *       edge crosses the boundary outward (fromv inside → tov outside). Fallback exit point
 *       for states that start at the boundary vertex.</li>
 *   <li><b>{@code forwardCrossingEntry}</b> — fromv is on the <em>outside</em>. The current
 *       edge crosses inward (fromv outside → tov inside). <em>Not normally enforced</em>: the
 *       entry decision should have fired one edge earlier via
 *       {@code forwardApproachingEntry}. Included for completeness so the strategy can opt in.</li>
 * </ul>
 *
 * <p>The first two ("approaching") are a symmetric pair — same shape, opposite direction —
 * both fire when the next edge will cross the boundary. The last two ("crossing") are also
 * a pair — same shape, opposite direction — both fire when the current edge crosses.
 *
 * <p>ArriveBy methods receive positions in <em>forward time</em>. The orchestrator adjusts
 * for the reverse-time search direction before dispatching.
 *
 * @see RestrictedZoneEnforcement
 * @see BusinessAreaEnforcement
 */
sealed interface GeofencingEnforcement permits BusinessAreaEnforcement, RestrictedZoneEnforcement {
  /** Forward search, tov is the outside-side boundary vertex; next edge enters the zone. */
  @Nullable
  default State[] forwardApproachingEntry(GeofencingZone zone, State state, EdgeTraversal edge) {
    return null;
  }

  /** Forward search, tov is the inside-side boundary vertex; next edge exits the zone. */
  @Nullable
  default State[] forwardApproachingExit(GeofencingZone zone, State state, EdgeTraversal edge) {
    return null;
  }

  /** Forward search, fromv is the inside-side boundary vertex; current edge exits the zone. */
  @Nullable
  default State[] forwardCrossingExit(GeofencingZone zone, State state, EdgeTraversal edge) {
    return null;
  }

  /**
   * Forward search, fromv is the outside-side boundary vertex; current edge enters the zone.
   *
   * <p>Not normally enforced. The fork decision for entry is made at the <em>previous</em>
   * edge by {@link #forwardApproachingEntry}, when this same vertex was the previous edge's
   * tov. Once the state arrives at the boundary vertex, the fork has already happened — the
   * state crosses silently and {@link StateEditor} updates the zone set via
   * {@link GeofencingBoundaryExtension#resolveZoneTransitions}.
   *
   * <p>Override this only if you need to enforce something for states that land on the
   * outside boundary vertex without having traversed an edge that fired
   * {@code forwardApproachingEntry} (e.g., initial states placed directly on the boundary).
   */
  @Nullable
  default State[] forwardCrossingEntry(GeofencingZone zone, State state, EdgeTraversal edge) {
    return null;
  }

  /**
   * Set-level invariant check on the state's resolved zone context. Unlike the position methods,
   * this isn't dispatched per zone — it's called once per traversal per strategy, with the full
   * set of zones the state is currently inside. Used by strategies whose invariants span the
   * whole set (e.g., "no traversal-banned zone is currently active" — a per-network
   * priority-resolved check, not a per-zone field inspection).
   */
  @Nullable
  default State[] enforceInside(Set<GeofencingZone> currentZones, State state, EdgeTraversal edge) {
    return null;
  }

  /**
   * ArriveBy search at a boundary where the state would (in forward time) be entering the zone.
   * Restricted zones block committed renting states from entering no-traversal zones.
   */
  @Nullable
  default State[] arriveByApproaching(GeofencingZone zone, State state, EdgeTraversal edge) {
    return null;
  }

  /**
   * ArriveBy search at a boundary where the state would (in forward time) be exiting the zone.
   * HAVE_RENTED walkers produce a walking branch; renting branches are deferred.
   */
  @Nullable
  default State[] arriveByAtBoundary(GeofencingZone zone, State state, EdgeTraversal edge) {
    return null;
  }

  /**
   * Resolve the appropriate enforcement implementation for a zone.
   */
  static GeofencingEnforcement forZone(GeofencingZone zone) {
    if (zone.isBusinessArea()) {
      return BusinessAreaEnforcement.INSTANCE;
    }
    return RestrictedZoneEnforcement.INSTANCE;
  }
}
