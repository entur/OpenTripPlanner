package org.opentripplanner.service.vehiclerental.street;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.opentripplanner.street.model.RentalRestrictionExtension;
import org.opentripplanner.street.search.state.State;

/**
 * Traversal is banned since this location is the border of a business area.
 */
public final class BusinessAreaBorder implements RentalRestrictionExtension {

  private final String network;

  public BusinessAreaBorder(String network) {
    this.network = network;
  }

  @Override
  public boolean traversalBanned(State state) {
    if (!state.isRentingVehicle()) {
      return false;
    }
    // Generic (uncommitted) states pass through freely — enforcement is deferred
    // to their committed branches which have a known network.
    if (state.getVehicleRentalNetwork() == null) {
      return false;
    }
    return network.equals(state.getVehicleRentalNetwork());
  }

  @Override
  public boolean dropOffBanned(State state) {
    return false;
  }

  @Override
  public Set<RestrictionType> debugTypes() {
    return EnumSet.of(RestrictionType.BUSINESS_AREA_BORDER);
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
