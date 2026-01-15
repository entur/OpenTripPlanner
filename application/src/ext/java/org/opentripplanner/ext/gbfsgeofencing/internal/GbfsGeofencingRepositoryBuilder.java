package org.opentripplanner.ext.gbfsgeofencing.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.opentripplanner.ext.gbfsgeofencing.GbfsGeofencingRepository;
import org.opentripplanner.service.vehiclerental.model.GeofencingZone;

/**
 * Builder for constructing {@link GbfsGeofencingRepository} during graph build.
 * <p>
 * This class is NOT thread-safe and should only be used during single-threaded graph build.
 * After build completes, call {@link #build()} to create an immutable repository.
 */
public class GbfsGeofencingRepositoryBuilder {

  private final List<GeofencingZone> geofencingZones = new ArrayList<>();
  private int modifiedEdgeCount = 0;

  /**
   * Add geofencing zones to the repository.
   */
  public void addGeofencingZones(Collection<GeofencingZone> zones) {
    geofencingZones.addAll(zones);
  }

  /**
   * Set the count of modified edges.
   */
  public void setModifiedEdgeCount(int count) {
    this.modifiedEdgeCount = count;
  }

  /**
   * Build an immutable {@link GbfsGeofencingRepository}.
   */
  public GbfsGeofencingRepository build() {
    return new DefaultGbfsGeofencingRepository(geofencingZones, modifiedEdgeCount);
  }
}
