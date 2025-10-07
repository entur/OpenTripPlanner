package org.opentripplanner.street.search.request;

import org.opentripplanner.routing.core.VehicleRoutingOptimizeType;

public class BicycleRequest {

  public static final BicycleRequest DEFAULT = new BicycleRequest();

  public double speed() {
    return 1;
  }

  public double reluctance() {
    return 1;
  }

  public WalkRequest walking() {
    return null;
  }

  public VehicleRoutingOptimizeType optimizeType() {
    return null;
  }

  public OptimizationTriangle optimizeTriangle() {
    return null;
  }

  public ParkingRequest parking() {
    return null;
  }
}
