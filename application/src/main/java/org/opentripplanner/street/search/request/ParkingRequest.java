package org.opentripplanner.street.search.request;

import java.time.Duration;
import org.opentripplanner.framework.model.Cost;
import org.opentripplanner.routing.api.request.preference.filter.VehicleParkingFilter;
import org.opentripplanner.routing.api.request.preference.filter.VehicleParkingSelect;

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

  public VehicleParkingSelect preferred() {
    return null;
  }

  public VehicleParkingFilter filter() {
    return null;
  }
}
