package org.opentripplanner.street.search.request;

public class RentalRequest {

  private final ModeSpecificRentalRequest bike;
  private final ModeSpecificRentalRequest car;
  private final ModeSpecificRentalRequest scooter;

  public RentalRequest(ModeSpecificRentalRequest bike, ModeSpecificRentalRequest car, ModeSpecificRentalRequest scooter) {
    this.bike = bike;
    this.car = car;
    this.scooter = scooter;
  }

  public ModeSpecificRentalRequest bike() {
    return bike;
  }

  public ModeSpecificRentalRequest car() {
    return car;
  }

  public ModeSpecificRentalRequest scooter() {
    return scooter;
  }
}
