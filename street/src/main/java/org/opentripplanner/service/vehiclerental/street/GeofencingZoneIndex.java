package org.opentripplanner.service.vehiclerental.street;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.index.strtree.STRtree;
import org.opentripplanner.service.vehiclerental.model.GeofencingZone;

/**
 * Spatial index for efficient geofencing zone containment queries.
 * Used at vehicle pickup to determine which zones the vehicle starts in,
 * enabling boundary-only geofencing where restrictions are tracked in routing state
 * rather than applied to all interior edges.
 */
public class GeofencingZoneIndex implements Serializable {

  private static final GeometryFactory GF = new GeometryFactory();
  private final STRtree spatialIndex;

  public GeofencingZoneIndex(Collection<GeofencingZone> zones) {
    this.spatialIndex = new STRtree();
    for (var zone : zones) {
      spatialIndex.insert(zone.geometry().getEnvelopeInternal(), zone);
    }
    spatialIndex.build();
  }

  /**
   * Find all zones containing the given point.
   * Called at vehicle pickup to initialize routing state with the zones
   * the vehicle is currently inside.
   *
   * @param point The coordinate to check for zone containment
   * @return Set of GeofencingZone objects that contain the point
   */
  public Set<GeofencingZone> getZonesContaining(Coordinate point) {
    var pointGeom = GF.createPoint(point);
    @SuppressWarnings("unchecked")
    var candidates = (List<GeofencingZone>) spatialIndex.query(new Envelope(point));
    return candidates
      .stream()
      .filter(zone -> zone.geometry().contains(pointGeom))
      .collect(Collectors.toSet());
  }

  /**
   * Find all zones containing the given point that have restrictions
   * (either drop-off banned or traversal banned).
   *
   * @param point The coordinate to check for zone containment
   * @return Set of GeofencingZone objects with restrictions that contain the point
   */
  public Set<GeofencingZone> getRestrictedZonesContaining(Coordinate point) {
    return getZonesContaining(point)
      .stream()
      .filter(GeofencingZone::hasRestriction)
      .collect(Collectors.toSet());
  }

  /**
   * Find all zones containing the given point that belong to the specified network.
   *
   * @param point The coordinate to check for zone containment
   * @param network The network ID to filter by
   * @return Set of GeofencingZone objects for the network that contain the point
   */
  public Set<GeofencingZone> getZonesContaining(Coordinate point, String network) {
    return getZonesContaining(point)
      .stream()
      .filter(zone -> zone.id().getFeedId().equals(network))
      .collect(Collectors.toSet());
  }

  /**
   * Check if the index is empty (no zones indexed).
   */
  public boolean isEmpty() {
    return spatialIndex.isEmpty();
  }

  /**
   * Get the total number of zones in the index.
   */
  public int size() {
    return spatialIndex.size();
  }
}
