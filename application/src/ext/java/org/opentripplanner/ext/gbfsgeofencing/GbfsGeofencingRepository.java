package org.opentripplanner.ext.gbfsgeofencing;

import java.io.Serializable;
import java.util.Collection;
import org.opentripplanner.service.vehiclerental.model.GeofencingZone;

/**
 * Repository for tracking GBFS geofencing zones loaded at build time.
 * This is primarily for observability - the actual restrictions are stored
 * on the vertices themselves.
 */
public interface GbfsGeofencingRepository extends Serializable {
  /**
   * Stores zones that were loaded from GBFS feeds during graph building. Called by the
   * graph builder after zones have already been applied to street edges. The stored zones
   * are used for observability and are serialized with the graph—they are not used for
   * routing decisions.
   */
  void addGeofencingZones(Collection<GeofencingZone> zones);

  /**
   * Returns the zones loaded at build time. The returned collection is unmodifiable.
   * Used for serialization and debugging/observability—not for routing, since actual
   * restrictions are stored on vertices via {@code RentalRestrictionExtension}.
   */
  Collection<GeofencingZone> getGeofencingZones();

  /**
   * Returns the count of street edges that had geofencing restrictions applied during
   * graph building. This metric is logged to help operators verify that zones were
   * applied as expected.
   */
  int getModifiedEdgeCount();

  /**
   * Records the number of edges modified by {@code GeofencingZoneApplier}. Called by the
   * graph builder after applying restrictions to street edges, enabling the count to be
   * logged and persisted with the graph for metrics.
   */
  void setModifiedEdgeCount(int count);

  /**
   * Convenience method to check if any zones were loaded without retrieving the full
   * collection. Useful for conditional logic that only applies when geofencing is active.
   */
  default boolean hasGeofencingZones() {
    return !getGeofencingZones().isEmpty();
  }
}
