package org.opentripplanner.street.search.request;

public class StopAccessibilityRequest {

  public boolean onlyConsiderAccessible() {
    return false;
  }

  public double unknownCost() {
    return 0;
  }

  public double inaccessibleCost() {
    return 0;
  }
}
