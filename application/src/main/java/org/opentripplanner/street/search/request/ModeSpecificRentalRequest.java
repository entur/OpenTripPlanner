package org.opentripplanner.street.search.request;

import java.time.Duration;
import java.util.Set;
import org.opentripplanner.framework.model.Cost;

public class ModeSpecificRentalRequest {
  private final Cost pickupCost;
  private final Cost dropOffCost;
  private final Duration pickupTime;
  private final Duration dropOffTime;

  public ModeSpecificRentalRequest(Cost pickupCost, Cost dropOffCost, Duration pickupTime, Duration dropOffTime) {
    this.pickupCost = pickupCost;
    this.dropOffCost = dropOffCost;
    this.pickupTime = pickupTime;
    this.dropOffTime = dropOffTime;
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
    return null;
  }

  public Set<String>bannedNetworks() {
    return null;
  }
}
