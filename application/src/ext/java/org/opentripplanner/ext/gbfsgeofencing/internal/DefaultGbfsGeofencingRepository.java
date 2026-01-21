package org.opentripplanner.ext.gbfsgeofencing.internal;

import java.util.Collection;
import java.util.List;
import org.opentripplanner.ext.gbfsgeofencing.GbfsGeofencingRepository;
import org.opentripplanner.service.vehiclerental.model.GeofencingZone;

/**
 * Immutable implementation of {@link GbfsGeofencingRepository}.
 * <p>
 * Instances are created during graph build via {@link GbfsGeofencingRepositoryBuilder}
 * and are safe for concurrent read access at runtime.
 */
public class DefaultGbfsGeofencingRepository implements GbfsGeofencingRepository {

  private final List<GeofencingZone> geofencingZones;
  private final int modifiedEdgeCount;

  public DefaultGbfsGeofencingRepository(
    Collection<GeofencingZone> geofencingZones,
    int modifiedEdgeCount
  ) {
    this.geofencingZones = List.copyOf(geofencingZones);
    this.modifiedEdgeCount = modifiedEdgeCount;
  }

  @Override
  public Collection<GeofencingZone> getGeofencingZones() {
    return geofencingZones;
  }

  @Override
  public int getModifiedEdgeCount() {
    return modifiedEdgeCount;
  }
}
