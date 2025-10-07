package org.opentripplanner.street.search.request;

import java.time.Duration;
import java.util.Set;
import org.opentripplanner.framework.model.Cost;

public class ModeSpecificRentalRequest {
  private final Cost pickupCost;
  private final Cost dropOffCost;
  private final Duration pickupTime;
  private final Duration dropOffTime;
  private final Set<String> allowedNetworks;
  private final Set<String> bannedNetworks;

  public ModeSpecificRentalRequest(Cost pickupCost, Cost dropOffCost, Duration pickupTime, Duration dropOffTime, Set<String> allowedNetworks, Set<String> bannedNetworks) {
    this.pickupCost = pickupCost;
    this.dropOffCost = dropOffCost;
    this.pickupTime = pickupTime;
    this.dropOffTime = dropOffTime;
    this.allowedNetworks = allowedNetworks;
    this.bannedNetworks = bannedNetworks;
  }

  public boolean useAvailabilityInformation() {
    return false;
  }

  public boolean allowArrivingInRentedVehicleAtDestination() {
    return false;
  }

  public Cost pickupCost() {
    return pickupCost;
  }

  public Cost dropOffCost() {
    return dropOffCost;
  }

  public Duration pickupTime() {
    return pickupTime;
  }

  public Duration dropOffTime() {
    return dropOffTime;
  }

  public Set<String> allowedNetworks() {
    return allowedNetworks;
  }

  public Set<String>bannedNetworks() {
    return bannedNetworks;
  }

  public Cost arrivingInRentalVehicleAtDestinationCost() {
    return null;
  }
}
