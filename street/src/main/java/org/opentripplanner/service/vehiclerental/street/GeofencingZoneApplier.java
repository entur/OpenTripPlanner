package org.opentripplanner.service.vehiclerental.street;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
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
 * {@link GeofencingBoundaryExtension} is applied to boundary-crossing edges for state-based zone
 * tracking. For business areas, {@link BusinessAreaBorder} is applied to boundary edges.
 */
public class GeofencingZoneApplier {

  private final Function<Collection<LineString>, Set<Edge>> findEdgesAlongLineStrings;
  private final Function<Envelope, Collection<Edge>> findEdgesForEnvelope;
  private final boolean applyBusinessAreas;

  public GeofencingZoneApplier(
    Function<Collection<LineString>, Set<Edge>> findEdgesAlongLineStrings,
    Function<Envelope, Collection<Edge>> findEdgesForEnvelope,
    boolean applyBusinessAreas
  ) {
    this.findEdgesAlongLineStrings = findEdgesAlongLineStrings;
    this.findEdgesForEnvelope = findEdgesForEnvelope;
    this.applyBusinessAreas = applyBusinessAreas;
  }

  /**
   * Applies the restrictions described in the geofencing zones to edges by adding
   * {@link RentalRestrictionExtension} to them, builds a spatial index, and identifies
   * boundary-crossing edges.
   */
  public GeofencingZoneApplierResult applyGeofencingZones(
    Collection<GeofencingZone> geofencingZones
  ) {
    var zoneIndex = new GeofencingZoneIndex(geofencingZones);

    // All zones with geometry get boundary extensions, not just restricted ones.
    // Permissive zones (e.g., station_parking) need boundary tracking so they enter/exit
    // state correctly and can override restrictions via per-field precedence.
    var zonesWithGeometry = geofencingZones
      .stream()
      .filter(z -> z.geometry() != null)
      .toList();

    var businessAreaEdges = new HashMap<StreetEdge, String>();

    // Boundary marking: apply GeofencingBoundaryExtension to boundary-crossing edges
    var boundaryEdges = addBoundaryExtensions(zonesWithGeometry);

    // Business area borders (deprecated — not a GBFS concept)
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
      Map.copyOf(boundaryEdges),
      zoneIndex
    );
  }

  /**
   * Pre-resolves the initial geofencing zone for each vehicle rental vertex by querying the
   * spatial index. The highest-priority (lowest priority value) zone is stored on the vertex.
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
   * Identifies boundary-crossing edges for each restricted zone and applies
   * {@link GeofencingBoundaryExtension}. A boundary-crossing edge has one vertex inside the zone
   * and one outside. The {@code entering} flag indicates whether traversing in the edge's natural
   * direction (fromv → tov) enters the zone.
   * <p>
   * Uses vertex coordinates to detect boundary crossings without decompressing the compact edge
   * geometry. An edge crosses the boundary if and only if its endpoints are on different sides
   * of the zone. A bounding-box pre-filter on vertex coordinates avoids expensive Point creation
   * and PreparedGeometry calls for the majority of candidates that are outside the zone envelope.
   */
  private Map<StreetEdge, GeofencingBoundaryExtension> addBoundaryExtensions(
    List<GeofencingZone> zones
  ) {
    var edgesUpdated = new HashMap<StreetEdge, GeofencingBoundaryExtension>();
    var gf = GeometryUtils.getGeometryFactory();
    // Reusable Point to avoid allocating a new object per covers() call
    var reusablePoint = gf.createPoint(new Coordinate(0, 0));

    for (GeofencingZone zone : zones) {
      var geom = zone.geometry();
      var preparedZone = PreparedGeometryFactory.prepare(geom);
      var zoneBBox = geom.getEnvelopeInternal();

      // Find candidate edges using the zone bounding box. The line-following spatial index
      // query (findEdgesAlongLineStrings) can miss edges in adjacent grid cells. Using the
      // zone envelope is broader but reliably catches all boundary-crossing edges. Interior
      // edges are filtered out cheaply: both vertices inside → fromInZone == toInZone → skip.
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
            edgesUpdated
          );
        }
      }
    }
    return edgesUpdated;
  }

  private static void addBoundaryIfCrossing(
    StreetEdge streetEdge,
    GeofencingZone zone,
    Envelope zoneBBox,
    PreparedGeometry preparedZone,
    Point reusablePoint,
    Map<Vertex, Boolean> vertexInZone,
    Map<StreetEdge, GeofencingBoundaryExtension> edgesUpdated
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
      var ext = new GeofencingBoundaryExtension(zone, toInZone);
      streetEdge.addGeofencingBoundary(ext);
      // Also add to tov with opposite entering flag so that the pairing check
      // in updateGeofencingZones works for all vertices, including permanent
      // split vertices that may only be tov of boundary-crossing edges.
      toVertex.addGeofencingBoundary(new GeofencingBoundaryExtension(zone, !toInZone));
      edgesUpdated.put(streetEdge, ext);
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

  /**
   * Apply a business area border extension to edges that cross the boundary of the polygon.
   * Uses vertex containment to detect boundary crossings without decompressing edge geometry.
   */
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
