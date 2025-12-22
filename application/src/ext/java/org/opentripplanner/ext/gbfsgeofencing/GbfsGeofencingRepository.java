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
   * Add geofencing zones to the repository.
   */
  void addGeofencingZones(Collection<GeofencingZone> zones);

  /**
   * Get all geofencing zones.
   */
  Collection<GeofencingZone> getGeofencingZones();

  /**
   * Get the number of street edges modified by geofencing zones.
   */
  int getModifiedEdgeCount();

  /**
   * Set the count of modified edges (for logging/metrics).
   */
  void setModifiedEdgeCount(int count);

  /**
   * Check if any geofencing zones were loaded.
   */
  default boolean hasGeofencingZones() {
    return !getGeofencingZones().isEmpty();
  }
}
