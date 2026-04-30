package org.opentripplanner.ext.carpooling.util;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nullable;
import org.opentripplanner.astar.model.GraphPath;
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
 * runs a bounded WALK A* to find the nearest such vertex. If the input is already stoppable the
 * result contains no walk path; otherwise the result carries the walk path the passenger uses to
 * bridge the pedestrian-only stretch.
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

  public record SnapResult(Vertex vertex, @Nullable GraphPath<State, Edge, Vertex> walkPath) {}

  /**
   * Snaps a pickup point: searches forward from {@code vertexToSnap} along outgoing edges to find
   * the nearest stoppable vertex within {@code maxWalk}. The returned walk path runs from
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
   * the nearest stoppable vertex within {@code maxWalk}. The returned walk path runs from the
   * snapped vertex to {@code vertexToSnap}.
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
   *        nearest stoppable vertex.
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

    // With MinimumWeight dominance and TRIVIAL heuristic the priority queue pops states in
    // weight order, so the first popped stoppable state is the minimum-weight one. Capture it
    // and terminate immediately rather than building the full SPT.
    var foundRef = new AtomicReference<State>();
    SearchTerminationStrategy<State> terminator = state -> {
      if (isStoppable(state.getVertex())) {
        foundRef.set(state);
        return true;
      }
      return false;
    };

    var request = StreetSearchRequest.copyOf(baseRequest)
      .withMode(StreetMode.WALK)
      .withArriveBy(arriveBy)
      .build();
    var builder = StreetSearchBuilder.of()
      .withRequest(request)
      .withSkipEdgeStrategy(new DurationSkipEdgeStrategy<>(maxWalk))
      .withDominanceFunction(new DominanceFunctions.MinimumWeight())
      .withTerminationStrategy(terminator);
    // AStarBuilder picks the initial-state vertex set based on arriveBy: toVertices for a reverse
    // search, fromVertices for a forward one. Route vertexToSnap to the matching side or
    // createInitialStates receives null.
    (arriveBy ? builder.withTo(vertexToSnap) : builder.withFrom(vertexToSnap)).run();

    State best = foundRef.get();
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
   * <p>
   * <strong>noThruTraffic edges are intentionally accepted here.</strong> A residential street
   * tagged {@code access=destination} permits cars locally and the standard A*'s no-thru state
   * machine only blocks a traversal that enters a noThru zone <em>from outside</em> and then
   * tries to exit. A driver whose trip origin or destination is itself inside the same noThru
   * zone never trips that block — so a carpool offered by a resident, picking up another resident,
   * routes correctly. Filtering noThru vertices here would silently drop such intra-neighbourhood
   * matches; instead we rely on the per-trip carpool A* to fail fast for the cases that genuinely
   * can't route through.
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
