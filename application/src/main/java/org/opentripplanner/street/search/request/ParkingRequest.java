package org.opentripplanner.street.search.request;

import java.time.Duration;
import org.opentripplanner.framework.model.Cost;
import org.opentripplanner.routing.api.request.preference.filter.VehicleParkingFilter;
import org.opentripplanner.routing.api.request.preference.filter.VehicleParkingSelect;

public class ParkingRequest {

  private final Cost cost;
  private final Duration time;
  private final Cost unpreferredVehicleParkingTagCost;
  private final VehicleParkingSelect preferred;
  private final VehicleParkingFilter filter;

  private ParkingRequest() {
    this.cost = null;
    this.time = null;
    this.unpreferredVehicleParkingTagCost = null;
    this.preferred = null;
    this.filter = null;
  }

  private ParkingRequest(Builder builder) {
    this.cost = builder.cost;
    this.time = builder.time;
    this.unpreferredVehicleParkingTagCost = builder.unpreferredVehicleParkingTagCost;
    this.preferred = builder.preferred;
    this.filter = builder.filter;
  }

  public static Builder of() {
    return new Builder();
  }

  public Cost cost() {
    return cost;
  }

  public Duration time() {
    return time;
  }

  public Cost unpreferredVehicleParkingTagCost() {
    return unpreferredVehicleParkingTagCost;
  }

  public VehicleParkingSelect preferred() {
    return preferred;
  }

  public VehicleParkingFilter filter() {
    return filter;
  }

  public static class Builder {

    private Cost cost = null;
    private Duration time = null;
    private Cost unpreferredVehicleParkingTagCost = null;
    private VehicleParkingSelect preferred = null;
    private VehicleParkingFilter filter = null;

    public Builder withCost(Cost cost) {
      this.cost = cost;
      return this;
    }

    public Builder withTime(Duration time) {
      this.time = time;
      return this;
    }

    public Builder withUnpreferredVehicleParkingTagCost(Cost unpreferredVehicleParkingTagCost) {
      this.unpreferredVehicleParkingTagCost = unpreferredVehicleParkingTagCost;
      return this;
    }

    public Builder withPreferred(VehicleParkingSelect preferred) {
      this.preferred = preferred;
      return this;
    }

    public Builder withFilter(VehicleParkingFilter filter) {
      this.filter = filter;
      return this;
    }

    public ParkingRequest build() {
      return new ParkingRequest(this);
    }
  }
}
