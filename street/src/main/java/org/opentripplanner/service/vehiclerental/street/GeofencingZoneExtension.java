package org.opentripplanner.service.vehiclerental.street;

/**
 * Traversal is restricted by the properties of the geofencing zone.
 */

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.opentripplanner.service.vehiclerental.model.GeofencingZone;
import org.opentripplanner.street.model.RentalRestrictionExtension;
import org.opentripplanner.street.search.state.State;

/**
 * Traversal is restricted by the properties of the geofencing zone.
 */
public final class GeofencingZoneExtension implements RentalRestrictionExtension {

  private final GeofencingZone zone;

  public GeofencingZoneExtension(GeofencingZone zone) {
    this.zone = zone;
  }

  public GeofencingZone zone() {
    return zone;
  }

  @Override
  public boolean traversalBanned(State state) {
    if (state.isRentingVehicle()) {
      return (
        Boolean.TRUE.equals(zone.traversalBanned()) &&
        (state.unknownRentalNetwork() ||
          zone.id().getFeedId().equals(state.getVehicleRentalNetwork()))
      );
    } else {
      return false;
    }
  }

  @Override
  public boolean dropOffBanned(State state) {
    if (state.isRentingVehicle()) {
      return (
        Boolean.TRUE.equals(zone.dropOffBanned()) &&
        zone.id().getFeedId().equals(state.getVehicleRentalNetwork())
      );
    } else {
      return false;
    }
  }

  @Override
  public Set<RestrictionType> debugTypes() {
    var set = EnumSet.noneOf(RestrictionType.class);
    if (Boolean.TRUE.equals(zone.traversalBanned())) {
      set.add(RestrictionType.NO_TRAVERSAL);
    }
    if (Boolean.TRUE.equals(zone.dropOffBanned())) {
      set.add(RestrictionType.NO_DROP_OFF);
    }
    return set;
  }

  @Override
  public List<RentalRestrictionExtension> toList() {
    return List.of(this);
  }

  @Override
  public List<String> networks() {
    return List.of(zone.id().getFeedId());
  }

  @Override
  public boolean hasRestrictions() {
    return true;
  }

  @Override
  public Set<String> noDropOffNetworks() {
    if (Boolean.TRUE.equals(zone.dropOffBanned())) {
      return Set.of(zone.id().getFeedId());
    } else {
      return Set.of();
    }
  }

  @Override
  public String toString() {
    return zone.id().toString();
  }
}
