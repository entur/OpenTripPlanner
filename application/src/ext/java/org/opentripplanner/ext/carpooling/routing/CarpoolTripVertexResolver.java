package org.opentripplanner.ext.carpooling.routing;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;
import org.opentripplanner.ext.carpooling.model.CarpoolTrip;
import org.opentripplanner.ext.carpooling.util.CarAccessibleVertexSnapper;
import org.opentripplanner.ext.carpooling.util.StreetVertexUtils;
import org.opentripplanner.routing.linking.internal.VertexCreationService;
import org.opentripplanner.street.geometry.SphericalDistanceLibrary;
import org.opentripplanner.street.geometry.WgsCoordinate;
import org.opentripplanner.street.linking.TemporaryVerticesContainer;
import org.opentripplanner.street.model.edge.Edge;
import org.opentripplanner.street.model.vertex.TemporaryVertex;
import org.opentripplanner.street.model.vertex.Vertex;
import org.opentripplanner.street.search.request.StreetSearchRequest;

/**
 * Resolves a {@link CarpoolTrip}'s route points onto permanent, car-reachable street graph
 * vertices, producing the {@link CarpoolTripWithVertices} the routing service consumes.
 * <p>
 * Resolution runs once per trip, at ingest, and never on a passenger request: the outcome depends
 * only on the trip's route-point geometry and the static street graph. The resolved vertices are
 * permanent graph vertices, so they can be stored with the trip for its whole lifetime — no
 * request-scoped linking is repeated per request, and every reachability verdict feeds the
 * snapper's cross-request cache.
 *
 * <h2>How a route point resolves</h2>
 * <ol>
 *   <li>The point is linked in CAR mode, exactly as the driver's own routing would link it. The
 *       linking is temporary and lives only for the duration of this call; it is used purely to
 *       borrow the linker's edge selection (closest car-traversable edge, duplicate-way and area
 *       handling).</li>
 *   <li>The permanent vertices at the boundary of that temporary linking — typically the two
 *       endpoints of the split edge — are tried in order of distance from the route point, and the
 *       first one a car can genuinely reach and leave is chosen. This needs no street search
 *       beyond the snapper's cached reachability probes, and it works on car-only roads where no
 *       pedestrian could walk.</li>
 *   <li>When no boundary vertex is car-reachable — the point sits in a car-inaccessible pocket
 *       such as a barrier-locked loop or a one-way trap — a bounded walk search nudges the point
 *       to the nearest genuinely reachable permanent vertex
 *       ({@link CarAccessibleVertexSnapper#snapToPermanentVertex}).</li>
 * </ol>
 * A trip with any unresolvable route point is not routable at all, so {@link #resolve} returns
 * {@code null} and the caller drops the trip.
 * <p>
 * The chosen vertex is an edge endpoint rather than a mid-edge split, so a trip's modeled route
 * passes through the nearest intersection instead of the exact feed coordinate. The offset is
 * bounded by the linked edge's length — seconds of driving against GPS-precision input — and it is
 * what makes the result permanent and cacheable.
 * <p>
 * This class is stateless apart from its collaborators and safe to call from concurrent updater
 * threads.
 */
public class CarpoolTripVertexResolver {

  /**
   * Walking-time budget for the fallback search that nudges a route point out of a
   * car-inaccessible pocket. This bounds how far a route point may drift from the driver's
   * advertised coordinate (roughly 400 m at default walk speed); a point needing more than this is
   * treated as unusable and the trip is rejected.
   */
  private static final Duration MAX_SNAP_WALK = Duration.ofMinutes(5);

  private final VertexCreationService vertexCreationService;
  private final CarAccessibleVertexSnapper carVertexSnapper;

  public CarpoolTripVertexResolver(
    VertexCreationService vertexCreationService,
    CarAccessibleVertexSnapper carVertexSnapper
  ) {
    this.vertexCreationService = vertexCreationService;
    this.carVertexSnapper = carVertexSnapper;
  }

  /**
   * Resolves every route point of {@code trip} to a permanent, car-reachable vertex, or returns
   * {@code null} when any point cannot be resolved — the trip is then unroutable and should be
   * dropped. All temporary linking created along the way is disposed before this returns.
   */
  @Nullable
  public CarpoolTripWithVertices resolve(CarpoolTrip trip) {
    try (var temporaryVerticesContainer = new TemporaryVerticesContainer()) {
      var streetVertexUtils = new StreetVertexUtils(
        vertexCreationService,
        temporaryVerticesContainer
      );
      var vertices = trip
        .routePoints()
        .stream()
        .map(point -> resolveRoutePoint(point, streetVertexUtils))
        .toList();
      if (vertices.stream().anyMatch(Objects::isNull)) {
        return null;
      }
      return new CarpoolTripWithVertices(trip, vertices);
    }
  }

  @Nullable
  private Vertex resolveRoutePoint(WgsCoordinate point, StreetVertexUtils streetVertexUtils) {
    var linked = streetVertexUtils.createDriverWaypointVertex(point);
    if (linked == null) {
      return null;
    }
    for (var candidate : permanentBoundary(linked, point)) {
      if (carVertexSnapper.isCarAccessible(candidate)) {
        return candidate;
      }
    }
    var snap = carVertexSnapper.snapToPermanentVertex(
      StreetSearchRequest.DEFAULT,
      linked,
      MAX_SNAP_WALK
    );
    return snap == null ? null : snap.vertex();
  }

  /**
   * The permanent vertices at the boundary of {@code linked}'s temporary subgraph — the endpoints
   * of the split edge(s), or directly-linked graph vertices — ordered by distance from the route
   * point. Only temporary vertices are expanded, so the walk never leaks into another concurrent
   * linking hanging off the same permanent vertices.
   */
  private static List<Vertex> permanentBoundary(Vertex linked, WgsCoordinate point) {
    var seen = new HashSet<Vertex>();
    var queue = new ArrayDeque<Vertex>();
    var boundary = new ArrayList<Vertex>();
    seen.add(linked);
    queue.add(linked);
    while (!queue.isEmpty()) {
      var current = queue.poll();
      for (var edges : List.of(current.getOutgoing(), current.getIncoming())) {
        for (Edge edge : edges) {
          for (var neighbor : List.of(edge.getFromVertex(), edge.getToVertex())) {
            if (!seen.add(neighbor)) {
              continue;
            }
            if (neighbor instanceof TemporaryVertex) {
              queue.add(neighbor);
            } else {
              boundary.add(neighbor);
            }
          }
        }
      }
    }
    boundary.sort(
      Comparator.comparingDouble(v ->
        SphericalDistanceLibrary.fastDistance(point.asJtsCoordinate(), v.getCoordinate())
      )
    );
    return boundary;
  }
}
