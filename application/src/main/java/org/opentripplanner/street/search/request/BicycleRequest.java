package org.opentripplanner.street.search.request;

import org.opentripplanner.routing.core.VehicleRoutingOptimizeType;

public class BicycleRequest {

  public static final BicycleRequest DEFAULT = new BicycleRequest();

  private final double speed;
  private final double reluctance;
  private final WalkRequest walking;
  private final VehicleRoutingOptimizeType optimizeType;
  private final OptimizationTriangle optimizeTriangle;
  private final ParkingRequest parking;

  private BicycleRequest() {
    this.speed = 1;
    this.reluctance = 1;
    this.walking = null;
    this.optimizeType = null;
    this.optimizeTriangle = null;
    this.parking = null;
  }

  private BicycleRequest(Builder builder) {
    this.speed = builder.speed;
    this.reluctance = builder.reluctance;
    this.walking = builder.walking;
    this.optimizeType = builder.optimizeType;
    this.optimizeTriangle = builder.optimizeTriangle;
    this.parking = builder.parking;
  }

  public static Builder of() {
    return new Builder();
  }

  public double speed() {
    return speed;
  }

  public double reluctance() {
    return reluctance;
  }

  public WalkRequest walking() {
    return walking;
  }

  public VehicleRoutingOptimizeType optimizeType() {
    return optimizeType;
  }

  public OptimizationTriangle optimizeTriangle() {
    return optimizeTriangle;
  }

  public ParkingRequest parking() {
    return parking;
  }

  public static class Builder {

    private double speed = 1;
    private double reluctance = 1;
    private WalkRequest walking = null;
    private VehicleRoutingOptimizeType optimizeType = null;
    private OptimizationTriangle optimizeTriangle = null;
    private ParkingRequest parking = null;

    public Builder withSpeed(double speed) {
      this.speed = speed;
      return this;
    }

    public Builder withReluctance(double reluctance) {
      this.reluctance = reluctance;
      return this;
    }

    public Builder withWalking(WalkRequest walking) {
      this.walking = walking;
      return this;
    }

    public Builder withOptimizeType(VehicleRoutingOptimizeType optimizeType) {
      this.optimizeType = optimizeType;
      return this;
    }

    public Builder withOptimizeTriangle(OptimizationTriangle optimizeTriangle) {
      this.optimizeTriangle = optimizeTriangle;
      return this;
    }

    public Builder withParking(ParkingRequest parking) {
      this.parking = parking;
      return this;
    }

    public BicycleRequest build() {
      return new BicycleRequest(this);
    }
  }
}
