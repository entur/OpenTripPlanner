package org.opentripplanner.service.vehiclerental.street;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.opentripplanner.service.vehiclerental.model.GeofencingZone;
import org.opentripplanner.street.model.RentalRestrictionExtension;
import org.opentripplanner.street.search.state.State;

/**
 * Marks a boundary-crossing edge for a geofencing zone. The extension itself does not enforce any
 * restrictions — it serves as a marker for state-based zone tracking in the routing algorithm.
 *
 * @param zone the geofencing zone whose boundary this edge crosses
 * @param entering true if traversing the edge in its natural direction (fromv → tov) enters the
 *     zone; false if it exits
 */
public record GeofencingBoundaryExtension(GeofencingZone zone, boolean entering) implements
  RentalRestrictionExtension {
  @Override
  public boolean traversalBanned(State state) {
    return false;
  }

  @Override
  public boolean dropOffBanned(State state) {
    return false;
  }

  @Override
  public Set<RestrictionType> debugTypes() {
    return EnumSet.of(RestrictionType.GEOFENCING_BOUNDARY);
  }

  @Override
  public List<RentalRestrictionExtension> toList() {
    return List.of(this);
  }

  @Override
  public boolean hasRestrictions() {
    return true;
  }
}
