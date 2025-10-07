package org.opentripplanner.street.search.request;

import java.time.Duration;
import org.opentripplanner.framework.model.Cost;
import org.opentripplanner.routing.alertpatch.EntitySelector;

public class ParkingRequest {
  public Cost cost() {
    return null;
  }

  public Duration time() {
    return null;
  }

  public Cost unpreferredVehicleParkingTagCost() {
    return null;
  }

  public EntitySelector preferred() {
    return null;
  }

}
