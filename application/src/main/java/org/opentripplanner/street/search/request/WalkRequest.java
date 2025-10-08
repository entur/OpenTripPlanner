package org.opentripplanner.street.search.request;

import java.time.Duration;
import org.opentripplanner.framework.model.Cost;

public class WalkRequest {
  private final double speed;
  private final double reluctance;
  private final double stairsTimeFactor;
  private final double safetyFactor;
  private final Cost mountDismountCost;
  private final Duration mountDismountTime;
  private final double stairsReluctance;
  private final EscalatorRequest escalator;

  private WalkRequest(Builder builder) {
    this.speed = builder.speed;
    this.reluctance = builder.reluctance;
    this.stairsTimeFactor = builder.stairsTimeFactor;
    this.safetyFactor = builder.safetyFactor;
    this.mountDismountCost = builder.mountDismountCost;
    this.mountDismountTime = builder.mountDismountTime;
    this.stairsReluctance = builder.stairsReluctance;
    this.escalator = builder.escalator;
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

  public double stairsTimeFactor() {
    return stairsTimeFactor;
  }

  public double safetyFactor() {
    return safetyFactor;
  }

  public Cost mountDismountCost() {
    return mountDismountCost;
  }

  public Duration mountDismountTime() {
    return mountDismountTime;
  }

  public double stairsReluctance() {
    return stairsReluctance;
  }

  public EscalatorRequest escalator() {
    return escalator;
  }

  public static class Builder {
    private double speed = 1;
    private double reluctance = 1;
    private double stairsTimeFactor = 1;
    private double safetyFactor = 1;
    private Cost mountDismountCost = Cost.costOfSeconds(120);
    private Duration mountDismountTime = Duration.ofSeconds(10);
    private double stairsReluctance = 0;
    private EscalatorRequest escalator = null;

    public Builder withSpeed(double speed) {
      this.speed = speed;
      return this;
    }

    public Builder withReluctance(double reluctance) {
      this.reluctance = reluctance;
      return this;
    }

    public Builder withStairsTimeFactor(double stairsTimeFactor) {
      this.stairsTimeFactor = stairsTimeFactor;
      return this;
    }

    public Builder withSafetyFactor(double safetyFactor) {
      this.safetyFactor = safetyFactor;
      return this;
    }

    public Builder withMountDismountCost(Cost mountDismountCost) {
      this.mountDismountCost = mountDismountCost;
      return this;
    }

    public Builder withMountDismountTime(Duration mountDismountTime) {
      this.mountDismountTime = mountDismountTime;
      return this;
    }

    public Builder withStairsReluctance(double stairsReluctance) {
      this.stairsReluctance = stairsReluctance;
      return this;
    }

    public Builder withEscalator(EscalatorRequest escalator) {
      this.escalator = escalator;
      return this;
    }

    public WalkRequest build() {
      return new WalkRequest(this);
    }
  }
}
}
