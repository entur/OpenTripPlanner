package org.opentripplanner.street.search.request;

public class WheelchairRequest {
  public static final WheelchairRequest DEFAULT = new WheelchairRequest();

  public double inaccessibleStreetReluctance() {
    return 0;
  }

  public double stairsReluctance() {
    return 0;
  }

  public double maxSlope() {
    return 0;
  }

  public double slopeExceededReluctance() {
    return 0;
  }
}
