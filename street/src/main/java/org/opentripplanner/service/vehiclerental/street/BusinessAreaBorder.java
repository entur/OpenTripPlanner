package org.opentripplanner.service.vehiclerental.street;

import org.opentripplanner.street.search.state.State;

/**
 * Marks a vertex as being on the border of a rental network's business area.
 * Traversal is banned for vehicles of the matching network — they cannot leave
 * the business area. Enforced via {@code Vertex.rentalTraversalBanned(State)}.
 */
public final class BusinessAreaBorder {

  private final String network;

  public BusinessAreaBorder(String network) {
    this.network = network;
  }

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

  public String network() {
    return network;
  }
}
