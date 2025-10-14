package org.opentripplanner.street.search.request;

import java.time.Duration;
import org.opentripplanner.framework.model.Cost;

public class CarRequest {

  private final double reluctance;
  private final Duration pickupTime;
  private final Cost pickupCost;
  private final ParkingRequest parking;

  private CarRequest() {
    this.reluctance = 1;
    this.pickupTime = Duration.ofMinutes(10);
    this.pickupCost = Cost.costOfSeconds(120);
    this.parking = null;
  }

  private CarRequest(Builder builder) {
    this.reluctance = builder.reluctance;
    this.pickupTime = builder.pickupTime;
    this.pickupCost = builder.pickupCost;
    this.parking = builder.parking;
  }

  public static Builder of() {
    return new Builder();
  }

  public double reluctance() {
    return reluctance;
  }

  public Duration pickupTime() {
    return pickupTime;
  }

  public Cost pickupCost() {
    return pickupCost;
  }

  public ParkingRequest parking() {
    return parking;
  }

  public static class Builder {

    private double reluctance = 1;
    private Duration pickupTime = Duration.ofMinutes(10);
    private Cost pickupCost = Cost.costOfSeconds(120);
    private ParkingRequest parking = null;

    public Builder withReluctance(double reluctance) {
      this.reluctance = reluctance;
      return this;
    }

    public Builder withPickupTime(Duration pickupTime) {
      this.pickupTime = pickupTime;
      return this;
    }

    public Builder withPickupCost(Cost pickupCost) {
      this.pickupCost = pickupCost;
      return this;
    }

    public Builder withParking(ParkingRequest parking) {
      this.parking = parking;
      return this;
    }

    public CarRequest build() {
      return new CarRequest(this);
    }
  }
}
