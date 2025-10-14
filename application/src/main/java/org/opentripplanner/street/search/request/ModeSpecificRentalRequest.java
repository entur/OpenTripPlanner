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
  private final boolean useAvailabilityInformation;
  private final boolean allowArrivingInRentedVehicleAtDestination;
  private final Cost arrivingInRentalVehicleAtDestinationCost;

  public ModeSpecificRentalRequest(
    Cost pickupCost,
    Cost dropOffCost,
    Duration pickupTime,
    Duration dropOffTime,
    Set<String> allowedNetworks,
    Set<String> bannedNetworks
  ) {
    this.pickupCost = pickupCost;
    this.dropOffCost = dropOffCost;
    this.pickupTime = pickupTime;
    this.dropOffTime = dropOffTime;
    this.allowedNetworks = allowedNetworks;
    this.bannedNetworks = bannedNetworks;
    this.useAvailabilityInformation = false;
    this.allowArrivingInRentedVehicleAtDestination = false;
    this.arrivingInRentalVehicleAtDestinationCost = null;
  }

  private ModeSpecificRentalRequest(Builder builder) {
    this.pickupCost = builder.pickupCost;
    this.dropOffCost = builder.dropOffCost;
    this.pickupTime = builder.pickupTime;
    this.dropOffTime = builder.dropOffTime;
    this.allowedNetworks = builder.allowedNetworks;
    this.bannedNetworks = builder.bannedNetworks;
    this.useAvailabilityInformation = builder.useAvailabilityInformation;
    this.allowArrivingInRentedVehicleAtDestination =
      builder.allowArrivingInRentedVehicleAtDestination;
    this.arrivingInRentalVehicleAtDestinationCost =
      builder.arrivingInRentalVehicleAtDestinationCost;
  }

  public static Builder of() {
    return new Builder();
  }

  public boolean useAvailabilityInformation() {
    return useAvailabilityInformation;
  }

  public boolean allowArrivingInRentedVehicleAtDestination() {
    return allowArrivingInRentedVehicleAtDestination;
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

  public Set<String> bannedNetworks() {
    return bannedNetworks;
  }

  public Cost arrivingInRentalVehicleAtDestinationCost() {
    return arrivingInRentalVehicleAtDestinationCost;
  }

  public static class Builder {

    private Cost pickupCost;
    private Cost dropOffCost;
    private Duration pickupTime;
    private Duration dropOffTime;
    private Set<String> allowedNetworks;
    private Set<String> bannedNetworks;
    private boolean useAvailabilityInformation = false;
    private boolean allowArrivingInRentedVehicleAtDestination = false;
    private Cost arrivingInRentalVehicleAtDestinationCost = null;

    public Builder withPickupCost(Cost pickupCost) {
      this.pickupCost = pickupCost;
      return this;
    }

    public Builder withDropOffCost(Cost dropOffCost) {
      this.dropOffCost = dropOffCost;
      return this;
    }

    public Builder withPickupTime(Duration pickupTime) {
      this.pickupTime = pickupTime;
      return this;
    }

    public Builder withDropOffTime(Duration dropOffTime) {
      this.dropOffTime = dropOffTime;
      return this;
    }

    public Builder withAllowedNetworks(Set<String> allowedNetworks) {
      this.allowedNetworks = allowedNetworks;
      return this;
    }

    public Builder withBannedNetworks(Set<String> bannedNetworks) {
      this.bannedNetworks = bannedNetworks;
      return this;
    }

    public Builder withUseAvailabilityInformation(boolean useAvailabilityInformation) {
      this.useAvailabilityInformation = useAvailabilityInformation;
      return this;
    }

    public Builder withAllowArrivingInRentedVehicleAtDestination(
      boolean allowArrivingInRentedVehicleAtDestination
    ) {
      this.allowArrivingInRentedVehicleAtDestination = allowArrivingInRentedVehicleAtDestination;
      return this;
    }

    public Builder withArrivingInRentalVehicleAtDestinationCost(
      Cost arrivingInRentalVehicleAtDestinationCost
    ) {
      this.arrivingInRentalVehicleAtDestinationCost = arrivingInRentalVehicleAtDestinationCost;
      return this;
    }

    public ModeSpecificRentalRequest build() {
      return new ModeSpecificRentalRequest(this);
    }
  }
}
