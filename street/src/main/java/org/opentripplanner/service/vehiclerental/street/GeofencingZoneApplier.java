package org.opentripplanner.service.vehiclerental.street;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;
import org.opentripplanner.service.vehiclerental.model.GeofencingZone;
import org.opentripplanner.street.geometry.GeometryUtils;
import org.opentripplanner.street.model.edge.Edge;
import org.opentripplanner.street.model.edge.StreetEdge;
import org.opentripplanner.street.model.vertex.Vertex;

/**
 * Applies geofencing zone restrictions to the street graph. For restricted zones,
 * {@link GeofencingBoundaryExtension} is applied to boundary-crossing vertices for state-based
 * zone tracking. The business area implementation ({@link BusinessAreaBorder}) is pre-existing and
 * retained behind a feature flag.
 */
public class GeofencingZoneApplier {

  private final Function<Envelope, Collection<Edge>> findEdgesForEnvelope;
  private final boolean applyBusinessAreas;

  public GeofencingZoneApplier(
    Function<Envelope, Collection<Edge>> findEdgesForEnvelope,
    boolean applyBusinessAreas
  ) {
    this.findEdgesForEnvelope = findEdgesForEnvelope;
    this.applyBusinessAreas = applyBusinessAreas;
  }

  /**
   * Applies the restrictions described in the geofencing zones to edges, builds a spatial index,
   * and identifies boundary-crossing edges.
   */
  public GeofencingZoneApplierResult applyGeofencingZones(
    Collection<GeofencingZone> geofencingZones
  ) {
    var zoneIndex = new GeofencingZoneIndex(geofencingZones);

    // All zones with geometry get boundary extensions, not just restricted ones.
    // Permissive zones need boundary tracking so they enter/exit state correctly
    // and can contribute values via per-field precedence.
    var zonesWithGeometry = geofencingZones
      .stream()
      .filter(z -> z.geometry() != null)
      .toList();

    var businessAreaEdges = new HashMap<StreetEdge, String>();

    var boundaryVertices = addBoundaryExtensions(zonesWithGeometry);

    if (applyBusinessAreas) {
      var businessAreasByNetwork = geofencingZones
        .stream()
        .filter(GeofencingZone::isBusinessArea)
        .collect(Collectors.groupingBy(z -> z.id().getFeedId()));

      for (var entry : businessAreasByNetwork.entrySet()) {
        var network = entry.getKey();
        var polygons = entry
          .getValue()
          .stream()
          .map(GeofencingZone::geometry)
          .toArray(Geometry[]::new);

        var unionOfBusinessAreas = GeometryUtils.getGeometryFactory()
          .createGeometryCollection(polygons)
          .union();

        businessAreaEdges.putAll(applyBusinessAreaBorder(unionOfBusinessAreas, network));
      }
    }

    return new GeofencingZoneApplierResult(
      Map.copyOf(businessAreaEdges),
      Set.copyOf(boundaryVertices),
      zoneIndex
    );
  }

  /**
   * Pre-resolves the initial geofencing zones for each vehicle rental vertex by querying the
   * spatial index. All containing zones are stored on the vertex so that the routing state is
   * correctly initialized when a rental begins.
   */
  public static void preResolveVertexZones(
    Collection<VehicleRentalPlaceVertex> vertices,
    GeofencingZoneIndex zoneIndex
  ) {
    for (var vertex : vertices) {
      var zones = zoneIndex.getZonesContaining(vertex.getCoordinate());
      vertex.setInitialGeofencingZones(Set.copyOf(zones));
    }
  }

  /**
   * Identifies boundary-crossing edges and applies {@link GeofencingBoundaryExtension}. A
   * boundary-crossing edge has one vertex inside the zone and one outside. Uses vertex coordinates
   * to detect crossings without decompressing the compact edge geometry.
   */
  private Set<Vertex> addBoundaryExtensions(List<GeofencingZone> zones) {
    var verticesUpdated = new HashSet<Vertex>();
    var gf = GeometryUtils.getGeometryFactory();
    var reusablePoint = gf.createPoint(new Coordinate(0, 0));

    for (GeofencingZone zone : zones) {
      var geom = zone.geometry();
      var preparedZone = PreparedGeometryFactory.prepare(geom);
      var zoneBBox = geom.getEnvelopeInternal();

      Collection<Edge> candidates = findEdgesForEnvelope.apply(zoneBBox);

      // Cache vertex containment per zone. Edges share vertices (typically 3-4 edges per
      // vertex), so caching avoids redundant PreparedGeometry.covers() calls.
      var vertexInZone = new HashMap<Vertex, Boolean>();

      for (var e : candidates) {
        if (e instanceof StreetEdge streetEdge) {
          addBoundaryIfCrossing(
            streetEdge,
            zone,
            zoneBBox,
            preparedZone,
            reusablePoint,
            vertexInZone,
            verticesUpdated
          );
        }
      }
    }
    return verticesUpdated;
  }

  private static void addBoundaryIfCrossing(
    StreetEdge streetEdge,
    GeofencingZone zone,
    Envelope zoneBBox,
    PreparedGeometry preparedZone,
    Point reusablePoint,
    Map<Vertex, Boolean> vertexInZone,
    Set<Vertex> verticesUpdated
  ) {
    var fromVertex = streetEdge.getFromVertex();
    var toVertex = streetEdge.getToVertex();

    boolean fromInZone = vertexInZone.computeIfAbsent(fromVertex, v ->
      isVertexInZone(v.getCoordinate(), zoneBBox, preparedZone, reusablePoint)
    );
    boolean toInZone = vertexInZone.computeIfAbsent(toVertex, v ->
      isVertexInZone(v.getCoordinate(), zoneBBox, preparedZone, reusablePoint)
    );

    if (fromInZone != toInZone) {
      fromVertex.addGeofencingBoundary(new GeofencingBoundaryExtension(zone, toInZone));
      toVertex.addGeofencingBoundary(new GeofencingBoundaryExtension(zone, !toInZone));
      verticesUpdated.add(fromVertex);
      verticesUpdated.add(toVertex);
    }
  }

  private static boolean isVertexInZone(
    Coordinate coord,
    Envelope zoneBBox,
    PreparedGeometry preparedZone,
    Point reusablePoint
  ) {
    if (!zoneBBox.contains(coord)) {
      return false;
    }
    reusablePoint.getCoordinateSequence().setOrdinate(0, 0, coord.x);
    reusablePoint.getCoordinateSequence().setOrdinate(0, 1, coord.y);
    reusablePoint.geometryChanged();
    return preparedZone.covers(reusablePoint);
  }

  private Map<StreetEdge, String> applyBusinessAreaBorder(Geometry polygon, String network) {
    var edgesUpdated = new HashMap<StreetEdge, String>();
    Set<Edge> candidates = Set.copyOf(findEdgesForEnvelope.apply(polygon.getEnvelopeInternal()));
    var preparedPolygon = PreparedGeometryFactory.prepare(polygon);
    var polygonBBox = polygon.getEnvelopeInternal();
    var gf = GeometryUtils.getGeometryFactory();

    for (var e : candidates) {
      if (e instanceof StreetEdge streetEdge) {
        var fromCoord = streetEdge.getFromVertex().getCoordinate();
        var toCoord = streetEdge.getToVertex().getCoordinate();

        boolean fromMayBeInZone = polygonBBox.contains(fromCoord);
        boolean toMayBeInZone = polygonBBox.contains(toCoord);
        if (!fromMayBeInZone && !toMayBeInZone) {
          continue;
        }

        boolean fromInZone = fromMayBeInZone && preparedPolygon.covers(gf.createPoint(fromCoord));
        boolean toInZone = toMayBeInZone && preparedPolygon.covers(gf.createPoint(toCoord));

        if (fromInZone != toInZone) {
          streetEdge.addBusinessAreaBorderNetwork(network);
          edgesUpdated.put(streetEdge, network);
        }
      }
    }
    return edgesUpdated;
  }
}
