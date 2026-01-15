package org.opentripplanner.ext.gbfsgeofencing.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.annotation.Nullable;
import org.opentripplanner.ext.gbfsgeofencing.GbfsGeofencingRepository;
import org.opentripplanner.service.vehiclerental.model.GeofencingZone;

/**
 * Builder for constructing {@link GbfsGeofencingRepository} during graph build.
 * <p>
 * This class is NOT thread-safe and should only be used during single-threaded graph build.
 * After build completes, call {@link #build()} to create an immutable repository.
 * The built repository is cached and can be retrieved via {@link #getBuiltRepository()}.
 */
public class GbfsGeofencingRepositoryBuilder {

  private final List<GeofencingZone> geofencingZones = new ArrayList<>();
  private int modifiedEdgeCount = 0;
  private GbfsGeofencingRepository builtRepository;

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
   * The built repository is cached and can be retrieved via {@link #getBuiltRepository()}.
   */
  public GbfsGeofencingRepository build() {
    this.builtRepository = new DefaultGbfsGeofencingRepository(geofencingZones, modifiedEdgeCount);
    return builtRepository;
  }

  /**
   * Get the repository that was built by calling {@link #build()}.
   *
   * @return the built repository, or null if {@link #build()} has not been called yet
   */
  @Nullable
  public GbfsGeofencingRepository getBuiltRepository() {
    return builtRepository;
  }
}
