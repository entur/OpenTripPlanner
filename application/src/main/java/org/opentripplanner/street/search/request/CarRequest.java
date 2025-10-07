package org.opentripplanner.street.search.request;

import java.time.Duration;
import org.opentripplanner.framework.model.Cost;

public class CarRequest {

  public double reluctance() {
    return 1;
  }

  public Duration pickupTime() {
    return Duration.ofMinutes(10);
  }

  public Cost pickupCost() {
    return Cost.costOfSeconds(120);
  }

  public ParkingRequest parking() {
    return null;
  }
}
