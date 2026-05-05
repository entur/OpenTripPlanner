package org.opentripplanner.ext.carpooling.util;

import java.time.Duration;
import javax.annotation.Nullable;
import org.opentripplanner.astar.model.GraphPath;
import org.opentripplanner.astar.spi.RemainingWeightHeuristic;
import org.opentripplanner.astar.spi.SearchTerminationStrategy;
import org.opentripplanner.astar.strategy.DurationSkipEdgeStrategy;
import org.opentripplanner.street.model.StreetMode;
import org.opentripplanner.street.model.edge.Edge;
import org.opentripplanner.street.model.edge.StreetEdge;
import org.opentripplanner.street.model.vertex.Vertex;
import org.opentripplanner.street.search.StreetSearchBuilder;
import org.opentripplanner.street.search.TraverseMode;
import org.opentripplanner.street.search.request.StreetSearchRequest;
import org.opentripplanner.street.search.state.State;
import org.opentripplanner.street.search.strategy.DominanceFunctions;

/**
 * Resolves a pickup/dropoff point that a carpool driver can actually stop at.
 * <p>
 * The input is one end of a leg the carpool participates in: the passenger's origin/destination
 * for a direct carpool, or a transit stop's street-linked vertex for an access/egress leg. This
 * returns the input vertex unchanged when it is already drivable in both directions, otherwise
 * runs a bounded WALK A* to find the cheapest such vertex by generalized walk weight (i.e. walk
 * time scaled by the request's reluctance, safety factor, and other preferences — not raw
 * distance). If the input is already stoppable the result contains no walk path; otherwise the
 * result carries the walk path the passenger uses to bridge the pedestrian-only stretch.
 * <p>
 * The bounded walk A* is capped by {@code maxWalk}; if no stoppable vertex is reachable within
 * that budget — or no walk-traversable edge exists out of the input vertex in the search
 * direction — {@code null} is returned and the caller should reject the carpool match.
 * <p>
 * Use {@link #snapPickup} when the walker departs from {@code vertexToSnap} (forward search,
 * outgoing edges) and {@link #snapDropoff} when the walker arrives at it (reverse search,
 * incoming edges). The returned walk path is always in chronological order, from the walk's
 * start vertex to its end vertex.
 */
public final class StoppableVertexSnapper {

  private StoppableVertexSnapper() {}

  /**
   * Outcome of a snap: a stoppable {@code vertex} the carpool driver can pick up or drop off at,
   * paired with the {@code walkPath} the passenger uses to bridge the pedestrian-only stretch
   * between that vertex and the original input.
   * <p>
   * {@code walkPath} is {@code null} in two cases: the input vertex was already stoppable (no walk
   * needed), or the snap landed on a zero-duration neighbour reached only through the
   * passenger-side {@code TemporaryStreetLocation}'s zero-cost {@code TemporaryFreeEdge}s — i.e.
   * the same physical point, just a different graph node, with no real walking to render.
   * <p>
   * When non-null, the path is always in chronological order: from {@code vertexToSnap} to
   * {@code vertex} for a pickup, and from {@code vertex} to {@code vertexToSnap} for a dropoff
   * (see {@link #snapPickup} / {@link #snapDropoff}).
   */
  public record SnapResult(Vertex vertex, @Nullable GraphPath<State, Edge, Vertex> walkPath) {}

  /**
   * Snaps a pickup point: searches forward from {@code vertexToSnap} along outgoing edges to find
   * the lowest-weight stoppable vertex within {@code maxWalk}. The returned walk path runs from
   * {@code vertexToSnap} to the snapped vertex.
   *
   * @see #snap for the parameter contract.
   */
  @Nullable
  public static SnapResult snapPickup(
    StreetSearchRequest baseRequest,
    Vertex vertexToSnap,
    Duration maxWalk
  ) {
    return snap(baseRequest, vertexToSnap, maxWalk, false);
  }

  /**
   * Snaps a dropoff point: searches backward to {@code vertexToSnap} along incoming edges to find
   * the lowest-weight stoppable vertex within {@code maxWalk}. The returned walk path runs from
   * the snapped vertex to {@code vertexToSnap}.
   *
   * @see #snap for the parameter contract.
   */
  @Nullable
  public static SnapResult snapDropoff(
    StreetSearchRequest baseRequest,
    Vertex vertexToSnap,
    Duration maxWalk
  ) {
    return snap(baseRequest, vertexToSnap, maxWalk, true);
  }

  /**
   * @param baseRequest a {@link StreetSearchRequest} carrying the user's walk preferences (speed,
   *        reluctance, safety factor, …). The walk A* runs with these values; mode is forced to
   *        {@link StreetMode#WALK} and {@code arriveBy} is overridden by the {@code arriveBy}
   *        parameter, so callers can hand in any request shape (typically the result of mapping
   *        their {@code RouteRequest}). Pass {@link StreetSearchRequest#DEFAULT} only when
   *        defaults are truly intended.
   * @param vertexToSnap one end of the leg the carpool participates in: a passenger
   *        origin/destination for a direct carpool, or a transit stop's street-linked vertex for
   *        an access/egress leg. The function returns this vertex unchanged when it is already
   *        stoppable, otherwise searches outward (or inward, when {@code arriveBy}) for the
   *        lowest-weight stoppable vertex.
   * @param maxWalk walking-time budget for reaching a stoppable vertex.
   * @param arriveBy {@code false} for a pickup (walker departs from {@code vertexToSnap}),
   *        {@code true} for a dropoff (walker arrives at it).
   * @return the snap result, or {@code null} if no stoppable vertex is reachable within
   *         {@code maxWalk}.
   */
  @Nullable
  private static SnapResult snap(
    StreetSearchRequest baseRequest,
    Vertex vertexToSnap,
    Duration maxWalk,
    boolean arriveBy
  ) {
    if (isStoppable(vertexToSnap)) {
      return new SnapResult(vertexToSnap, null);
    }

    // We want the cheapest stoppable vertex within maxWalk of the input. A* isn't built for
    // "first vertex matching a predicate" — it normally stops at a fixed target or once it has
    // explored the full walk shed. So we repurpose its termination strategy as our predicate:
    // the strategy is called once per expanded state in cost-ascending order (this ordering
    // depends on the dominance function and heuristic configured below — see the package
    // discussion for why), so the first state we see at a stoppable vertex is guaranteed to be
    // the cheapest one reachable. The strategy only returns a boolean, so we stash the winning
    // state in foundRef on the side and read it back after the search returns. The one-element
    // array is the standard Java escape hatch for mutating a captured local from a lambda — the
    // search runs single-threaded so no synchronization is needed.
    State[] foundRef = new State[1];
    SearchTerminationStrategy<State> terminator = state -> {
      if (isStoppable(state.getVertex())) {
        foundRef[0] = state;
        return true;
      }
      return false;
    };

    var request = StreetSearchRequest.copyOf(baseRequest)
      .withMode(StreetMode.WALK)
      .withArriveBy(arriveBy)
      .build();
    // Pin the heuristic explicitly: the termination strategy fires per state in cost-ascending
    // order only when expansion is by g (actual weight), not by f = g + h. The trivial heuristic
    // returns 0 so f == g; any other heuristic would let a state with smaller g be expanded after
    // a state with larger g but smaller h, breaking the "first match is cheapest" guarantee.
    // AStarBuilder defaults to TRIVIAL today, but we don't want a future default flip to silently
    // break this snapper.
    var builder = StreetSearchBuilder.of()
      .withRequest(request)
      .withHeuristic(RemainingWeightHeuristic.TRIVIAL)
      .withSkipEdgeStrategy(new DurationSkipEdgeStrategy<>(maxWalk))
      .withDominanceFunction(new DominanceFunctions.MinimumWeight())
      .withTerminationStrategy(terminator);
    // AStarBuilder picks the initial-state vertex set based on arriveBy: toVertices for a reverse
    // search, fromVertices for a forward one. Route vertexToSnap to the matching side or
    // createInitialStates receives null.
    (arriveBy ? builder.withTo(vertexToSnap) : builder.withFrom(vertexToSnap)).run();

    State best = foundRef[0];
    if (best == null) {
      return null;
    }

    var path = new GraphPath<>(best);
    // The passenger-side TemporaryStreetLocation links to its split vertices via zero-cost
    // TemporaryFreeEdges; if the snap landed on one of those, there's no real walk to render.
    if (path.getDuration() == 0) {
      return new SnapResult(best.getVertex(), null);
    }
    return new SnapResult(best.getVertex(), path);
  }

  /**
   * A vertex is stoppable when the carpool driver can both <em>arrive</em> at it and
   * <em>leave</em> it by car — i.e. there is at least one incoming car-permitting street edge
   * <em>and</em> at least one outgoing car-permitting street edge. Both directions are required
   * because carpool pickups and dropoffs are mid-route insertions; a vertex with only one (e.g.
   * the pedestrian-side end of a one-way drivable exit) is unrouteable for the carpool A*.
   * <p>
   * Motorway interiors are excluded naturally — the bounded WALK A* never reaches them, since
   * pedestrians can't walk onto a motorway in the first place.
   */
  private static boolean isStoppable(Vertex vertex) {
    return (
      anyStreetEdgeAllowsCar(vertex.getIncoming()) && anyStreetEdgeAllowsCar(vertex.getOutgoing())
    );
  }

  private static boolean anyStreetEdgeAllowsCar(Iterable<Edge> edges) {
    for (Edge e : edges) {
      if (e instanceof StreetEdge se && se.getPermission().allows(TraverseMode.CAR)) {
        return true;
      }
    }
    return false;
  }
}
