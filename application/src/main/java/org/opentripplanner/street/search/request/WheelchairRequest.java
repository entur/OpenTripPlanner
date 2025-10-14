package org.opentripplanner.street.search.request;

import org.opentripplanner.routing.api.request.preference.WheelchairPreferences;

public class WheelchairRequest {

  public static final WheelchairRequest DEFAULT = new WheelchairRequest();

  private final double inaccessibleStreetReluctance;
  private final double stairsReluctance;
  private final double maxSlope;
  private final double slopeExceededReluctance;
  private final AccessibilityRequest stop;
  private final AccessibilityRequest elevator;

  private WheelchairRequest() {
    this.inaccessibleStreetReluctance = 0;
    this.stairsReluctance = 0;
    this.maxSlope = 0;
    this.slopeExceededReluctance = 0;
    this.stop = null;
    this.elevator = null;
  }

  private WheelchairRequest(Builder builder) {
    this.inaccessibleStreetReluctance = builder.inaccessibleStreetReluctance;
    this.stairsReluctance = builder.stairsReluctance;
    this.maxSlope = builder.maxSlope;
    this.slopeExceededReluctance = builder.slopeExceededReluctance;
    this.stop = builder.stop;
    this.elevator = builder.elevator;
  }

  public static Builder of() {
    return new Builder();
  }

  public double inaccessibleStreetReluctance() {
    return inaccessibleStreetReluctance;
  }

  public double stairsReluctance() {
    return stairsReluctance;
  }

  public double maxSlope() {
    return maxSlope;
  }

  public double slopeExceededReluctance() {
    return slopeExceededReluctance;
  }

  public AccessibilityRequest stop() {
    return stop;
  }

  public AccessibilityRequest elevator() {
    return elevator;
  }

  public static class Builder {

    private double inaccessibleStreetReluctance = 0;
    private double stairsReluctance = 0;
    private double maxSlope = 0;
    private double slopeExceededReluctance = 0;
    private AccessibilityRequest stop = null;
    private AccessibilityRequest elevator = null;

    public Builder withInaccessibleStreetReluctance(double inaccessibleStreetReluctance) {
      this.inaccessibleStreetReluctance = inaccessibleStreetReluctance;
      return this;
    }

    public Builder withStairsReluctance(double stairsReluctance) {
      this.stairsReluctance = stairsReluctance;
      return this;
    }

    public Builder withMaxSlope(double maxSlope) {
      this.maxSlope = maxSlope;
      return this;
    }

    public Builder withSlopeExceededReluctance(double slopeExceededReluctance) {
      this.slopeExceededReluctance = slopeExceededReluctance;
      return this;
    }

    public Builder withStop(AccessibilityRequest stop) {
      this.stop = stop;
      return this;
    }

    public Builder withElevator(AccessibilityRequest elevator) {
      this.elevator = elevator;
      return this;
    }

    public WheelchairRequest build() {
      return new WheelchairRequest(this);
    }

    public WheelchairPreferences.Builder withStopOnlyAccessible() {
      this.
      return this;
    }
  }
}
