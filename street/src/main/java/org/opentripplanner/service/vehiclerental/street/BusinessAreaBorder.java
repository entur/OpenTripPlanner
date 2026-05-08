package org.opentripplanner.service.vehiclerental.street;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.opentripplanner.street.model.RentalRestrictionExtension;
import org.opentripplanner.street.search.state.State;

/**
 * Marks a vertex as being on the border of one or more rental networks' business areas. Traversal
 * is banned for vehicles of any matching network — they cannot leave their business area.
 */
public final class BusinessAreaBorder implements RentalRestrictionExtension {

  private final Set<String> networks;

  public BusinessAreaBorder() {
    this.networks = new HashSet<>();
  }

  public BusinessAreaBorder(String network) {
    this.networks = new HashSet<>();
    this.networks.add(network);
  }

  public void addNetwork(String network) {
    networks.add(network);
  }

  public void removeNetwork(String network) {
    networks.remove(network);
  }

  public boolean isEmpty() {
    return networks.isEmpty();
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
    return networks.contains(state.getVehicleRentalNetwork());
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

  @Override
  public Set<String> noDropOffNetworks() {
    return Set.of();
  }

  @Override
  public List<String> networks() {
    return List.copyOf(networks);
  }
}
