package org.opentripplanner.service.vehiclerental.street;

import java.util.HashSet;
import java.util.Set;
import org.opentripplanner.street.search.state.State;

/**
 * Marks a vertex as being on the border of one or more rental networks' business areas.
 * Traversal is banned for vehicles of any matching network — they cannot leave
 * their business area. Enforced via {@code Vertex.rentalTraversalBanned(State)}.
 *
 * @deprecated Business areas are an OTP-specific concept not defined in the GBFS spec.
 *     They are inferred from GBFS zones with no restrictions, but this inference is
 *     unreliable and the enforcement logic is not standardized. Disable via the
 *     {@code geofencingBusinessAreaBorders} updater configuration option.
 *     May be removed in a future version.
 */
@Deprecated
public final class BusinessAreaBorder {

  private final Set<String> networks;

  public BusinessAreaBorder() {
    this.networks = new HashSet<>();
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

  public Set<String> networks() {
    return Set.copyOf(networks);
  }
}
