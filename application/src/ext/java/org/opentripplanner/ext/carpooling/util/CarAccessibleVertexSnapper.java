package org.opentripplanner.ext.carpooling.util;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import org.locationtech.jts.geom.Coordinate;
import org.opentripplanner.astar.model.GraphPath;
import org.opentripplanner.astar.spi.SearchTerminationStrategy;
import org.opentripplanner.astar.spi.SkipEdgeStrategy;
import org.opentripplanner.astar.strategy.ComposingSkipEdgeStrategy;
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
 * driver trip waypoint. The input vertex is returned unchanged (with no walk path) when it is
 * already car-accessible; otherwise a bounded WALK A* finds the cheapest car-accessible vertex by
 * generalized walk weight (walk time scaled by the request's reluctance, safety factor, and other
 * preferences — not raw distance), and the result carries the walk path the passenger uses to
 * bridge the pedestrian-only stretch. If no car-accessible vertex is reachable within
 * {@code maxWalk}, {@code null} is returned and the caller should reject the carpool match.
 * <p>
 * "Car-accessible" is evaluated for a required {@link CarAccessDirection} in two stages, each
 * restricted to the required direction(s). First a cheap local pre-filter: a car-permitting street
 * edge out of the vertex when departure is required, into it when arrival is. Then a bounded
 * reachability probe: a short CAR search proving a car can actually drive at least
 * {@link #DEFAULT_MIN_CAR_ESCAPE_METERS} away from the vertex, and/or reach it from that far away.
 * The probe catches what the pre-filter cannot see — an edge may permit cars "on paper" while the
 * vertex is stranded on a one-way stretch with no legal approach, cut off by a barrier or
 * no-through-traffic zone, or sitting on a small disconnected island. It runs in
 * {@link StreetMode#CAR} against the plain street graph, so it honours exactly the restrictions
 * the driver's own routing honours, and its per-vertex, per-direction verdict is cached across
 * requests (see {@link #canEscape}).
 * <p>
 * Use {@link #snapPickup} when the walker departs from {@code vertexToSnap} (forward search,
 * outgoing edges) and {@link #snapDropoff} when the walker arrives at it (reverse search, incoming
 * edges). The returned walk path is always in chronological order, from the walk's start vertex to
 * its end vertex. {@link #snapToPermanentVertex} additionally restricts the accepted target to
 * permanent graph vertices, for callers that store the result beyond the current linking.
 */
public final class CarAccessibleVertexSnapper {

  /**
   * A candidate stopping point is accepted only if a car can drive at least this far
   * (straight-line, in metres) away from it and/or be reached from at least this far away: large
   * enough that a vertex on the connected road network clears it in a few blocks, small enough
   * that a stranded one-way stub, barrier pocket, or disconnected island cannot. The same distance
   * caps the probe's exploration.
   */
  private static final double DEFAULT_MIN_CAR_ESCAPE_METERS = 500;

  private static final StreetSearchRequest CAR_DEPART = StreetSearchRequest.of()
    .withMode(StreetMode.CAR)
    .build();
  private static final StreetSearchRequest CAR_ARRIVE = StreetSearchRequest.copyOf(CAR_DEPART)
    .withArriveBy(true)
    .build();

  private final double minCarEscapeMeters;

  /**
   * Per-vertex reachability verdicts: whether a car can drive away from the vertex
   * ({@code canDepartCache}) and whether one can reach it ({@code canArriveCache}) — cached
   * independently because on a one-way network they genuinely differ and a vertex is often probed
   * for one direction only. Keyed by permanent graph vertex; request-scoped temporary vertices are
   * never cached (they never recur, so caching them would only leak). A verdict depends only on
   * the static street graph and the escape distance, both fixed for the lifetime of this snapper,
   * so the caches are valid across requests. Growth is bounded by the permanent vertices actually
   * probed, which is acceptable for the lifetime of a server.
   */
  private final Map<Vertex, Boolean> canDepartCache = new ConcurrentHashMap<>();
  private final Map<Vertex, Boolean> canArriveCache = new ConcurrentHashMap<>();

  /**
   * @param minCarEscapeMeters straight-line distance a car must be able to drive away from, and be
   *        reached from, for a candidate stopping point to be accepted. Also caps how far the
   *        reachability probe explores.
   */
  public CarAccessibleVertexSnapper(double minCarEscapeMeters) {
    this.minCarEscapeMeters = minCarEscapeMeters;
  }

  /** Creates a snapper with the production escape distance. */
  public static CarAccessibleVertexSnapper createDefault() {
    return new CarAccessibleVertexSnapper(DEFAULT_MIN_CAR_ESCAPE_METERS);
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
    return snap(baseRequest, vertexToSnap, maxWalk, false, false, CarAccessDirection.THROUGH);
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
    return snap(baseRequest, vertexToSnap, maxWalk, true, false, CarAccessDirection.THROUGH);
  }

  /**
   * Like {@link #snapPickup}, but accepts only permanent graph vertices, never request-scoped
   * temporary ones, and requires the snapped vertex to be car-accessible in the given {@code access}
   * direction. Use when the result must outlive the temporary linking that produced
   * {@code vertexToSnap}: the linked vertex and its split neighbours are disposed with their
   * {@code TemporaryVerticesContainer}, while the vertex returned here can be stored — e.g. for the
   * lifetime of a carpool trip. Pass {@link CarAccessDirection#DEPART} for a trip origin and
   * {@link CarAccessDirection#ARRIVE} for a destination, so a one-directional endpoint is not
   * rejected for lacking the other direction. The walk search runs backward (arriving at
   * {@code vertexToSnap}) for {@code ARRIVE} and forward otherwise, mirroring the direction a driver
   * traverses the point.
   */
  @Nullable
  public SnapResult snapToPermanentVertex(
    StreetSearchRequest baseRequest,
    Vertex vertexToSnap,
    Duration maxWalk,
    CarAccessDirection access
  ) {
    boolean arriveBy = access == CarAccessDirection.ARRIVE;
    return snap(baseRequest, vertexToSnap, maxWalk, arriveBy, true, access);
  }

  /**
   * @param baseRequest carries the user's walk preferences (speed, reluctance, safety factor, …)
   *        for the walk A*; mode is forced to {@link StreetMode#WALK} and {@code arriveBy} is
   *        overridden by the parameter, so any request shape can be handed in. Pass
   *        {@link StreetSearchRequest#DEFAULT} only when defaults are truly intended. The
   *        reachability probe ignores this request — it always runs a plain
   *        {@link StreetMode#CAR} search so its verdict is request-independent.
   * @param vertexToSnap one end of the leg the carpool participates in (see the class doc).
   *        Returned unchanged when already acceptable, otherwise the search runs outward (or
   *        inward, when {@code arriveBy}) from it.
   * @param maxWalk walking-time budget for reaching a car-accessible vertex.
   * @param arriveBy {@code false} for a pickup (walker departs from {@code vertexToSnap}),
   *        {@code true} for a dropoff (walker arrives at it).
   * @param permanentOnly when {@code true}, request-scoped temporary vertices are never accepted
   *        as the snap target, so the result can outlive the linking that produced the input.
   * @param access the car-travel direction(s) the snapped vertex must support. Independent of
   *        {@code arriveBy}, which only steers the walk search: a pickup and a dropoff both require
   *        {@link CarAccessDirection#THROUGH} access yet walk in opposite directions.
   * @return the snap result, or {@code null} if no acceptable vertex is reachable within
   *         {@code maxWalk}.
   */
  @Nullable
  private SnapResult snap(
    StreetSearchRequest baseRequest,
    Vertex vertexToSnap,
    Duration maxWalk,
    boolean arriveBy,
    boolean permanentOnly,
    CarAccessDirection access
  ) {
    // The shared street graph exposes every concurrent request's temporary linking; the walk and
    // the reachability probes confine temporary-edge traversal to this search's own linking (see
    // isForeignTempEdge).
    var ownTempVertices = ownLinking(vertexToSnap);

    if (isAcceptableTarget(vertexToSnap, permanentOnly, access, ownTempVertices)) {
      return new SnapResult(vertexToSnap, null);
    }

    // A* has no "first vertex matching a predicate" mode, so the termination strategy doubles as
    // the predicate: it is called once per expanded state in cost-ascending order (see the package
    // discussion), so the first car-accessible vertex it sees is the cheapest reachable one. The
    // strategy returns only a boolean, so the winning state is stashed in foundRef.

    State[] foundRef = new State[1];
    SearchTerminationStrategy<State> terminator = state -> {
      if (isAcceptableTarget(state.getVertex(), permanentOnly, access, ownTempVertices)) {
        foundRef[0] = state;
        return true;
      }
      return false;
    };

    var request = StreetSearchRequest.copyOf(baseRequest)
      .withMode(StreetMode.WALK)
      .withArriveBy(arriveBy)
      .build();
    // No heuristic is available since there is no fixed destination.
    var builder = StreetSearchBuilder.of()
      .withRequest(request)
      .withSkipEdgeStrategy(
        new ComposingSkipEdgeStrategy<>(
          new ForeignTempEdgeSkipStrategy(ownTempVertices),
          new DurationSkipEdgeStrategy<>(maxWalk)
        )
      )
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

  private boolean isAcceptableTarget(
    Vertex vertex,
    boolean permanentOnly,
    CarAccessDirection access,
    Set<Vertex> ownTempVertices
  ) {
    if (permanentOnly && vertex instanceof TemporaryVertex) {
      return false;
    }
    return isCarAccessible(vertex, access, ownTempVertices);
  }

  /**
   * Whether {@code vertex} is car-accessible for {@code access} on the static street graph alone
   * (every temporary edge is ignored). To evaluate a temporary vertex against its own request's
   * linking, use the {@code ownTempVertices}-carrying overload from within a snap.
   */
  public boolean isCarAccessible(Vertex vertex, CarAccessDirection access) {
    return isCarAccessible(vertex, access, Set.of());
  }

  /**
   * A vertex is car-accessible for {@code access} when it clears the cheap local pre-filter (a
   * car-permitting street edge in the required direction(s)) <em>and</em> the reachability probe
   * proves a car can genuinely leave and/or reach it. The pre-filter runs first because it rejects
   * the vast majority of walk-shed vertices without any routing.
   *
   * @param ownTempVertices the temporary vertices of this search's own linking; the probe may cross
   *        the temporary edges among them but no foreign ones. Empty for a static-graph check.
   */
  private boolean isCarAccessible(
    Vertex vertex,
    CarAccessDirection access,
    Set<Vertex> ownTempVertices
  ) {
    if (access.requiresDeparture() && !anyStreetEdgeAllowsCar(vertex.getOutgoing())) {
      return false;
    }
    if (access.requiresArrival() && !anyStreetEdgeAllowsCar(vertex.getIncoming())) {
      return false;
    }
    if (access.requiresDeparture() && !canEscape(vertex, false, ownTempVertices)) {
      return false;
    }
    if (access.requiresArrival() && !canEscape(vertex, true, ownTempVertices)) {
      return false;
    }
    return true;
  }

  /**
   * Returns whether a car can escape {@code vertex} in one direction — drive away from it when
   * {@code arriveBy} is {@code false}, reach it when {@code true}.
   * <p>
   * A permanent vertex is probed against the static street graph only and its verdict is cached:
   * request-scoped edges must never influence a verdict that outlives the request. A temporary
   * vertex is probed without caching (it never recurs), and its probe may cross the temporary
   * edges of its own linking — often its only connection to the graph — but never a foreign one.
   */
  private boolean canEscape(Vertex vertex, boolean arriveBy, Set<Vertex> ownTempVertices) {
    if (vertex instanceof TemporaryVertex) {
      return probeEscapes(vertex, arriveBy, ownTempVertices);
    }
    var cache = arriveBy ? canArriveCache : canDepartCache;
    Boolean cached = cache.get(vertex);
    if (cached != null) {
      return cached;
    }
    // A probe is far too slow to run under a ConcurrentHashMap bin lock (as computeIfAbsent
    // would); a concurrent duplicate probe is harmless — both compute the same verdict.
    boolean verdict = probeEscapes(vertex, arriveBy, Set.of());
    cache.putIfAbsent(vertex, verdict);
    return verdict;
  }

  /**
   * Runs a bounded CAR search from {@code origin} and reports whether it settles any vertex at
   * least {@link #minCarEscapeMeters} away (straight-line) — outward when {@code arriveBy} is
   * {@code false}, proving the car can leave, inward when {@code true}, proving it can be reached.
   * The search terminates the instant it settles a vertex far enough away; as a Dijkstra settling
   * in weight order it typically visits most car-reachable vertices inside the escape-distance
   * ball first, while a stranded vertex is cheaper, exhausting only its small pocket.
   */
  private boolean probeEscapes(Vertex origin, boolean arriveBy, Set<Vertex> ownTempVertices) {
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
      .withSkipEdgeStrategy(new ForeignTempEdgeSkipStrategy(ownTempVertices))
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
   * The temporary vertices of {@code start}'s own linking: every temporary vertex reachable from
   * {@code start} without passing through a permanent vertex. Foreign linkings attach only to the
   * shared permanent graph, so this traversal can never cross into another request's temporary
   * subgraph. Empty when {@code start} is permanent, so a search rooted there admits no temporary
   * edge at all.
   */
  private static Set<Vertex> ownLinking(Vertex start) {
    if (!(start instanceof TemporaryVertex)) {
      return Set.of();
    }
    var own = new HashSet<Vertex>();
    var queue = new ArrayDeque<Vertex>();
    own.add(start);
    queue.add(start);
    while (!queue.isEmpty()) {
      var current = queue.poll();
      for (var edges : List.of(current.getOutgoing(), current.getIncoming())) {
        for (Edge edge : edges) {
          for (var neighbor : List.of(edge.getFromVertex(), edge.getToVertex())) {
            if (neighbor instanceof TemporaryVertex && own.add(neighbor)) {
              queue.add(neighbor);
            }
          }
        }
      }
    }
    return own;
  }

  /**
   * A temporary edge is foreign — belonging to another request's linking, not this search's — when
   * neither endpoint is one of {@code ownTempVertices}. Foreign temporary edges must never be
   * traversed: {@code TemporaryFreeEdge}s perform no mode check, so a CAR probe or walk could
   * escape through a mode-blind bridge no car can drive, making the verdict depend on unrelated
   * in-flight requests. Skipping them never disconnects the real graph — temporary splits are
   * non-destructive and leave the permanent edge in place. With an empty {@code ownTempVertices},
   * every temporary edge is foreign and the search sees the static graph only.
   */
  private static boolean isForeignTempEdge(Edge edge, Set<Vertex> ownTempVertices) {
    return (
      edge instanceof TemporaryEdge &&
      !ownTempVertices.contains(edge.getFromVertex()) &&
      !ownTempVertices.contains(edge.getToVertex())
    );
  }

  /**
   * Skips foreign temporary edges (see {@link #isForeignTempEdge}). The probe uses it alone — the
   * escape-distance termination already bounds its exploration — while the snap's walk A* composes
   * it with a {@link DurationSkipEdgeStrategy} capping the walk at {@code maxWalk}.
   */
  private record ForeignTempEdgeSkipStrategy(Set<Vertex> ownTempVertices) implements
    SkipEdgeStrategy<State, Edge> {
    @Override
    public boolean shouldSkipEdge(State current, Edge edge) {
      return isForeignTempEdge(edge, ownTempVertices);
    }
  }
}
