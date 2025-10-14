package org.opentripplanner.street.search.request;

public class EscalatorRequest {

  private final double speed;
  private final double reluctance;

  private EscalatorRequest() {
    this.speed = 0;
    this.reluctance = 0;
  }

  private EscalatorRequest(Builder builder) {
    this.speed = builder.speed;
    this.reluctance = builder.reluctance;
  }

  public static Builder of() {
    return new Builder();
  }

  public double speed() {
    return speed;
  }

  public double reluctance() {
    return reluctance;
  }

  public static class Builder {

    private double speed = 0;
    private double reluctance = 0;

    public Builder withSpeed(double speed) {
      this.speed = speed;
      return this;
    }

    public Builder withReluctance(double reluctance) {
      this.reluctance = reluctance;
      return this;
    }

    public EscalatorRequest build() {
      return new EscalatorRequest(this);
    }
  }
}
