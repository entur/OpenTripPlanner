package org.opentripplanner.street.search.request;

public class AccessibilityRequest {

  private final boolean onlyConsiderAccessible;
  private final double unknownCost;
  private final double inaccessibleCost;

  public AccessibilityRequest(
    boolean onlyConsiderAccessible,
    double unknownCost,
    double inaccessibleCost
  ) {
    this.onlyConsiderAccessible = onlyConsiderAccessible;
    this.unknownCost = unknownCost;
    this.inaccessibleCost = inaccessibleCost;
  }

  public static AccessibilityRequest ofOnlyAccessible() {
    return new AccessibilityRequest(true, 999_999, 999_999);
  }

  public boolean onlyConsiderAccessible() {
    return onlyConsiderAccessible;
  }

  public double unknownCost() {
    return unknownCost;
  }

  public double inaccessibleCost() {
    return inaccessibleCost;
  }
}
