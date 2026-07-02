package org.opentripplanner.ext.carpooling.util;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import org.locationtech.jts.geom.Coordinate;
import org.opentripplanner.astar.model.GraphPath;
import org.opentripplanner.astar.spi.SearchTerminationStrategy;
import org.opentripplanner.astar.spi.SkipEdgeStrategy;
import org.opentripplanner.astar.strategy.DurationSkipEdgeStrategy;
import org.opentripplanner.street.geometry.SphericalDistanceLibrary;
import org.opentripplanner.street.model.StreetMode;
import org.opentripplanner.street.model.edge.Edge;
import org.opentripplanner.street.model.edge.StreetEdge;
import org.opentripplanner.street.model.edge.TemporaryEdge;
import org.opentripplanner.street.model.vertex.TemporaryVertex;
import org.opentripplanner.street.model.vertex.Vertex;
import org.opentripplanner.street.search.StreetSearchBuilder;
import org.opentripplanner.street.search.TraverseMode;
import org.opentripplanner.street.search.request.StreetSearchRequest;
import org.opentripplanner.street.search.state.State;
import org.opentripplanner.street.search.strategy.DominanceFunctions;

/**
 * Resolves a vertex a car can genuinely route through near a target location — used to pick a
 * pickup or dropoff point for a carpool when the passenger's origin/destination (or a transit
 * stop's street-linked vertex) sits on a pedestrian-only edge, or on a car edge that no driver can
 * actually reach.
 * <p>
 * The input is one end of a leg the carpool participates in: the passenger's origin/destination
 * for a direct carpool, a transit stop's street-linked vertex for an access/egress leg, or a
 * driver trip waypoint. This returns the input vertex unchanged when it is already car-accessible,
 * otherwise runs a bounded WALK A* to find the cheapest car-accessible vertex by generalized walk
 * weight (i.e. walk time scaled by the request's reluctance, safety factor, and other preferences
 * — not raw distance). If the input is already car-accessible the result contains no walk path;
 * otherwise the result carries the walk path the passenger uses to bridge the pedestrian-only
 * stretch.
 *
 * <h2>What "car-accessible" means here</h2>
 *
 * A vertex is car-accessible only when a car can genuinely reach it <em>and</em> leave it — both
 * directions are required because carpool pickups and dropoffs are mid-route insertions the driver
 * passes through. The check has two stages:
 * <ol>
 *   <li>A cheap local pre-filter: the vertex must have at least one incoming and one outgoing
 *       car-permitting street edge. This rejects pedestrian-only vertices and one-directional
 *       stubs without any routing.</li>
 *   <li>A bounded bidirectional reachability probe: a short CAR search must be able to both drive
 *       away from the vertex to a point at least {@link #DEFAULT_MIN_CAR_ESCAPE_METERS} away
 *       <em>and</em> reach it from a point at least that far away. This is what the local
 *       pre-filter cannot see: an edge may permit cars "on paper" while the vertex is stranded on a
 *       one-way stretch with no legal approach, cut off by a barrier or no-through-traffic zone, or
 *       sitting on a small disconnected island. Such a vertex passes the local check but no car can
 *       actually route to it, so the probe rejects it and the search walks on to a curb a driver
 *       can truly use.</li>
 * </ol>
 * The probe runs in {@link StreetMode#CAR} against the plain street graph, so it honours exactly
 * the restrictions the driver's own routing honours (one-way, barriers, no-through-traffic). It is
 * bounded to a small radius ({@link #DEFAULT_PROBE_RADIUS_METERS}) and terminates as soon as it
 * escapes far enough, so a well-connected vertex costs only a handful of edge expansions. The
 * per-vertex verdict is cached for permanent vertices; those probes skip temporary, request-scoped
 * edges, so the verdict is a pure function of the static street graph and stays valid for the
 * lifetime of this snapper.
 * <p>
 * The bounded walk A* is capped by {@code maxWalk}; if no car-accessible vertex is reachable within
 * that budget — or no walk-traversable edge exists out of the input vertex in the search direction
 * — {@code null} is returned and the caller should reject the carpool match.
 * <p>
 * Use {@link #snapPickup} when the walker departs from {@code vertexToSnap} (forward search,
 * outgoing edges) and {@link #snapDropoff} when the walker arrives at it (reverse search, incoming
 * edges). The returned walk path is always in chronological order, from the walk's start vertex to
 * its end vertex.
 */
public final class CarAccessibleVertexSnapper {

  /**
   * A candidate curb is accepted only if a car can drive at least this far (straight-line, in
   * metres) away from it and be reached from at least this far away. Large enough that a curb on
   * the connected road network clears it in a few blocks, small enough that a stranded one-way
   * stub, barrier pocket, or disconnected island cannot.
   */
  private static final double DEFAULT_MIN_CAR_ESCAPE_METERS = 500;

  /**
   * Hard cap on how far (straight-line, in metres) the reachability probe explores before giving
   * up. Slightly larger than {@link #DEFAULT_MIN_CAR_ESCAPE_METERS} so a curb that can just barely
   * escape is still accepted, while a stranded curb's probe terminates promptly instead of
   * flooding the network.
   */
  private static final double DEFAULT_PROBE_RADIUS_METERS = 600;

  private static final StreetSearchRequest CAR_DEPART = StreetSearchRequest.of()
    .withMode(StreetMode.CAR)
    .build();
  private static final StreetSearchRequest CAR_ARRIVE = StreetSearchRequest.copyOf(CAR_DEPART)
    .withArriveBy(true)
    .build();

  private final double minCarEscapeMeters;
  private final double probeRadiusMeters;

  /**
   * Per-vertex reachability verdicts. Keyed by permanent graph vertex; request-scoped temporary
   * vertices are never cached (they are unique per request, so caching them would only leak). The
   * verdict depends only on the static street graph and the two distance bounds, both fixed for the
   * lifetime of this snapper, so the cache is valid across requests. Growth is monotonic but
   * bounded by the permanent vertices actually probed — only car-plausible vertices near carpool
   * activity — which is acceptable for the lifetime of a server.
   */
  private final Map<Vertex, Boolean> reachabilityCache = new ConcurrentHashMap<>();

  /**
   * @param minCarEscapeMeters straight-line distance a car must be able to drive away from, and be
   *        reached from, for a candidate curb to be accepted.
   * @param probeRadiusMeters straight-line cap on how far the reachability probe explores. Must be
   *        at least {@code minCarEscapeMeters}, otherwise no curb could ever escape.
   */
  public CarAccessibleVertexSnapper(double minCarEscapeMeters, double probeRadiusMeters) {
    this.minCarEscapeMeters = minCarEscapeMeters;
    this.probeRadiusMeters = probeRadiusMeters;
  }

  /** Creates a snapper with the production distance bounds. */
  public static CarAccessibleVertexSnapper createDefault() {
    return new CarAccessibleVertexSnapper(
      DEFAULT_MIN_CAR_ESCAPE_METERS,
      DEFAULT_PROBE_RADIUS_METERS
    );
  }

  /**
   * Outcome of a snap: a car-accessible {@code vertex} the carpool driver can pick up or drop off
   * at, paired with the {@code walkPath} the passenger uses to bridge the pedestrian-only stretch
   * between that vertex and the original input.
   * <p>
   * {@code walkPath} is {@code null} in two cases: the input vertex was already car-accessible (no
   * walk needed), or the snap landed on a zero-duration neighbour reached only through the
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
   * the lowest-weight car-accessible vertex within {@code maxWalk}. The returned walk path runs
   * from {@code vertexToSnap} to the snapped vertex.
   *
   * @see #snap for the parameter contract.
   */
  @Nullable
  public SnapResult snapPickup(
    StreetSearchRequest baseRequest,
    Vertex vertexToSnap,
    Duration maxWalk
  ) {
    return snap(baseRequest, vertexToSnap, maxWalk, false);
  }

  /**
   * Snaps a dropoff point: searches backward to {@code vertexToSnap} along incoming edges to find
   * the lowest-weight car-accessible vertex within {@code maxWalk}. The returned walk path runs
   * from the snapped vertex to {@code vertexToSnap}.
   *
   * @see #snap for the parameter contract.
   */
  @Nullable
  public SnapResult snapDropoff(
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
   *        defaults are truly intended. The reachability probe does not use this request; it always
   *        runs a plain {@link StreetMode#CAR} search so its verdict is request-independent.
   * @param vertexToSnap one end of the leg the carpool participates in: a passenger
   *        origin/destination for a direct carpool, a transit stop's street-linked vertex for an
   *        access/egress leg, or a driver trip waypoint. The function returns this vertex unchanged
   *        when it is already car-accessible, otherwise searches outward (or inward, when
   *        {@code arriveBy}) for the lowest-weight car-accessible vertex.
   * @param maxWalk walking-time budget for reaching a car-accessible vertex.
   * @param arriveBy {@code false} for a pickup (walker departs from {@code vertexToSnap}),
   *        {@code true} for a dropoff (walker arrives at it).
   * @return the snap result, or {@code null} if no car-accessible vertex is reachable within
   *         {@code maxWalk}.
   */
  @Nullable
  private SnapResult snap(
    StreetSearchRequest baseRequest,
    Vertex vertexToSnap,
    Duration maxWalk,
    boolean arriveBy
  ) {
    if (isCarAccessible(vertexToSnap)) {
      return new SnapResult(vertexToSnap, null);
    }

    // We want the cheapest car-accessible vertex within maxWalk of the input. A* isn't built for
    // "first vertex matching a predicate" — it normally terminates at a fixed target or once it has
    // explored the full walk shed. So we repurpose its termination strategy as our predicate:
    // the strategy is called once per expanded state in cost-ascending order (this ordering
    // depends on the dominance function and heuristic configured below — see the package
    // discussion for why), so the first state we see at a car-accessible vertex is guaranteed to
    // be the cheapest one reachable. The strategy only returns a boolean, so we stash the winning
    // state in foundRef on the side and read it back after the search returns. The one-element
    // array is the standard Java escape hatch for mutating a captured local from a lambda — the
    // search runs single-threaded so no synchronization is needed.
    State[] foundRef = new State[1];
    SearchTerminationStrategy<State> terminator = state -> {
      if (isCarAccessible(state.getVertex())) {
        foundRef[0] = state;
        return true;
      }
      return false;
    };

    var request = StreetSearchRequest.copyOf(baseRequest)
      .withMode(StreetMode.WALK)
      .withArriveBy(arriveBy)
      .build();
    // We can't use a heuristic since there isn't a fixed destination.
    var builder = StreetSearchBuilder.of()
      .withRequest(request)
      .withSkipEdgeStrategy(new DurationSkipEdgeStrategy<>(maxWalk))
      .withDominanceFunction(new DominanceFunctions.MinimumWeight())
      .withTerminationStrategy(terminator);
    // AStarBuilder picks the initial-state vertex set based on arriveBy: toVertices for a reverse
    // search, fromVertices for a forward one. Route vertexToSnap to the matching side or
    // createInitialStates receives null.
    if (arriveBy) {
      builder = builder.withTo(vertexToSnap);
    } else {
      builder = builder.withFrom(vertexToSnap);
    }
    builder.run();

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
   * A vertex is car-accessible when it clears the cheap local pre-filter (one incoming and one
   * outgoing car-permitting street edge) <em>and</em> a car can genuinely reach it and leave it
   * (the bidirectional reachability probe). The pre-filter runs first because it rejects the vast
   * majority of walk-shed vertices without any routing; only a vertex that already looks drivable
   * pays for the probe.
   */
  public boolean isCarAccessible(Vertex vertex) {
    if (
      !anyStreetEdgeAllowsCar(vertex.getIncoming()) || !anyStreetEdgeAllowsCar(vertex.getOutgoing())
    ) {
      return false;
    }
    return isCarReachable(vertex);
  }

  /**
   * Returns whether a car can both drive away from {@code vertex} and reach it, caching the verdict
   * for permanent graph vertices. Temporary, request-scoped vertices are probed directly without
   * caching — they never recur, so caching them would only retain garbage. Their probes may
   * traverse temporary edges (the vertex's own link is often its only connection to the graph),
   * while probes for permanent vertices ignore temporary edges entirely: a verdict influenced by
   * some request's transient linking must never outlive it in the cache.
   */
  private boolean isCarReachable(Vertex vertex) {
    if (vertex instanceof TemporaryVertex) {
      return probeBothDirections(vertex, false);
    }
    Boolean cached = reachabilityCache.get(vertex);
    if (cached != null) {
      return cached;
    }
    // Probed outside the map operation: a probe runs two bounded street searches, far too slow to
    // hold a ConcurrentHashMap bin lock for. A concurrent duplicate probe is harmless — both
    // compute the same verdict.
    boolean verdict = probeBothDirections(vertex, true);
    reachabilityCache.putIfAbsent(vertex, verdict);
    return verdict;
  }

  /**
   * Returns whether a car can both drive away from {@code vertex} and reach it — the two
   * independent directions a mid-route pickup or dropoff insertion requires. On a one-way network
   * these genuinely differ, so both must hold.
   */
  private boolean probeBothDirections(Vertex vertex, boolean permanentEdgesOnly) {
    return (
      probeEscapes(vertex, false, permanentEdgesOnly) &&
      probeEscapes(vertex, true, permanentEdgesOnly)
    );
  }

  /**
   * Runs a bounded CAR search from {@code origin} and reports whether it settles any vertex at
   * least {@link #minCarEscapeMeters} away (straight-line). With {@code arriveBy == false} the
   * search drives outward, proving the car can leave {@code origin}; with {@code arriveBy == true}
   * it searches inward, proving the car can reach {@code origin}. The search is capped at
   * {@link #probeRadiusMeters} by a skip-edge strategy and terminates the instant it escapes, so a
   * connected curb costs a few expansions and a stranded curb exhausts only its small pocket.
   */
  private boolean probeEscapes(Vertex origin, boolean arriveBy, boolean permanentEdgesOnly) {
    Coordinate originCoordinate = origin.getCoordinate();
    boolean[] escaped = new boolean[1];
    SearchTerminationStrategy<State> terminator = state -> {
      if (
        SphericalDistanceLibrary.fastDistance(
          originCoordinate,
          state.getVertex().getCoordinate()
        ) >=
        minCarEscapeMeters
      ) {
        escaped[0] = true;
        return true;
      }
      return false;
    };

    var builder = StreetSearchBuilder.of()
      .withRequest(arriveBy ? CAR_ARRIVE : CAR_DEPART)
      .withSkipEdgeStrategy(
        new ProbeSkipEdgeStrategy(originCoordinate, probeRadiusMeters, permanentEdgesOnly)
      )
      .withDominanceFunction(new DominanceFunctions.MinimumWeight())
      .withTerminationStrategy(terminator);
    if (arriveBy) {
      builder = builder.withTo(origin);
    } else {
      builder = builder.withFrom(origin);
    }
    builder.run();

    return escaped[0];
  }

  private static boolean anyStreetEdgeAllowsCar(Iterable<Edge> edges) {
    for (Edge e : edges) {
      if (e instanceof StreetEdge se && se.getPermission().allows(TraverseMode.CAR)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Stops expanding once the search has reached a vertex more than {@code maxMeters} (straight-line)
   * from the probe origin, keeping the reachability probe local and bounded. The distance is
   * measured to the current (already-settled) vertex rather than an edge endpoint so the bound
   * applies symmetrically to the forward (depart) probe and the reverse (arrive) probe, where edge
   * orientation is flipped.
   * <p>
   * With {@code permanentEdgesOnly}, temporary request-scoped edges are also skipped so the probe
   * sees only the permanent street graph. Temporary linking is visible to every concurrent search,
   * and {@code TemporaryFreeEdge}s perform no mode check, so without this a CAR probe could escape
   * through a temporary hub no car can actually drive through — and a cached verdict that leaned on
   * someone's transient link would outlive the link itself.
   */
  private record ProbeSkipEdgeStrategy(
    Coordinate origin,
    double maxMeters,
    boolean permanentEdgesOnly
  ) implements SkipEdgeStrategy<State, Edge> {
    @Override
    public boolean shouldSkipEdge(State current, Edge edge) {
      if (permanentEdgesOnly && edge instanceof TemporaryEdge) {
        return true;
      }
      return (
        SphericalDistanceLibrary.fastDistance(origin, current.getVertex().getCoordinate()) >
        maxMeters
      );
    }
  }
}
