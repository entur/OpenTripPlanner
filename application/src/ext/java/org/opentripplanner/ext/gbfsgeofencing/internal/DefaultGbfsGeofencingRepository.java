package org.opentripplanner.ext.gbfsgeofencing.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.opentripplanner.ext.gbfsgeofencing.GbfsGeofencingRepository;
import org.opentripplanner.service.vehiclerental.model.GeofencingZone;

public class DefaultGbfsGeofencingRepository implements GbfsGeofencingRepository {

  private final List<GeofencingZone> geofencingZones = new ArrayList<>();
  private int modifiedEdgeCount = 0;

  @Override
  public void addGeofencingZones(Collection<GeofencingZone> zones) {
    geofencingZones.addAll(zones);
  }

  @Override
  public Collection<GeofencingZone> getGeofencingZones() {
    return Collections.unmodifiableCollection(geofencingZones);
  }

  @Override
  public int getModifiedEdgeCount() {
    return modifiedEdgeCount;
  }

  @Override
  public void setModifiedEdgeCount(int count) {
    this.modifiedEdgeCount = count;
  }
}
