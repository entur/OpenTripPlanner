package org.opentripplanner.updater.trip.model;

import java.util.Objects;
import org.opentripplanner.updater.trip.gtfs.BackwardsDelayPropagationType;
import org.opentripplanner.updater.trip.gtfs.ForwardsDelayPropagationType;

/**
 * Configuration options that control how a trip update is processed.
 * Different real-time feed formats may have different defaults.
 */
public final class TripUpdateOptions {

  private final ForwardsDelayPropagationType forwardsPropagation;
  private final BackwardsDelayPropagationType backwardsPropagation;
  private final boolean allowStopPatternModification;

  /**
   * @param forwardsPropagation How delays should be propagated to future stops
   * @param backwardsPropagation How delays should be propagated to past stops
   * @param allowStopPatternModification Whether stop pattern modifications are allowed
   */
  public TripUpdateOptions(
    ForwardsDelayPropagationType forwardsPropagation,
    BackwardsDelayPropagationType backwardsPropagation,
    boolean allowStopPatternModification
  ) {
    this.forwardsPropagation = Objects.requireNonNull(
      forwardsPropagation,
      "forwardsPropagation must not be null"
    );
    this.backwardsPropagation = Objects.requireNonNull(
      backwardsPropagation,
      "backwardsPropagation must not be null"
    );
    this.allowStopPatternModification = allowStopPatternModification;
  }

  /**
   * Returns default options for SIRI-ET updates.
   * SIRI provides explicit times for all stops, so no delay interpolation is needed.
   */
  public static TripUpdateOptions siriDefaults() {
    return new TripUpdateOptions(
      ForwardsDelayPropagationType.NONE,
      BackwardsDelayPropagationType.NONE,
      true
    );
  }

  /**
   * Returns default options for GTFS-RT updates.
   * GTFS-RT may need delay interpolation for stops without explicit times.
   */
  public static TripUpdateOptions gtfsRtDefaults(
    ForwardsDelayPropagationType forwardsPropagation,
    BackwardsDelayPropagationType backwardsPropagation
  ) {
    return new TripUpdateOptions(forwardsPropagation, backwardsPropagation, true);
  }

  /**
   * Create a builder for custom options.
   */
  public static Builder builder() {
    return new Builder();
  }

  public ForwardsDelayPropagationType forwardsPropagation() {
    return forwardsPropagation;
  }

  public BackwardsDelayPropagationType backwardsPropagation() {
    return backwardsPropagation;
  }

  public boolean allowStopPatternModification() {
    return allowStopPatternModification;
  }

  /**
   * Returns true if this configuration propagates delays (forward or backward).
   */
  public boolean propagatesDelays() {
    return (
      forwardsPropagation != ForwardsDelayPropagationType.NONE ||
      backwardsPropagation != BackwardsDelayPropagationType.NONE
    );
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    TripUpdateOptions that = (TripUpdateOptions) o;
    return (
      allowStopPatternModification == that.allowStopPatternModification &&
      forwardsPropagation == that.forwardsPropagation &&
      backwardsPropagation == that.backwardsPropagation
    );
  }

  @Override
  public int hashCode() {
    return Objects.hash(forwardsPropagation, backwardsPropagation, allowStopPatternModification);
  }

  @Override
  public String toString() {
    return (
      "TripUpdateOptions{" +
      "forwardsPropagation=" +
      forwardsPropagation +
      ", backwardsPropagation=" +
      backwardsPropagation +
      ", allowStopPatternModification=" +
      allowStopPatternModification +
      '}'
    );
  }

  /**
   * Builder for TripUpdateOptions.
   */
  public static class Builder {

    private ForwardsDelayPropagationType forwardsPropagation = ForwardsDelayPropagationType.NONE;
    private BackwardsDelayPropagationType backwardsPropagation = BackwardsDelayPropagationType.NONE;
    private boolean allowStopPatternModification = true;

    public Builder withForwardsPropagation(ForwardsDelayPropagationType forwardsPropagation) {
      this.forwardsPropagation = forwardsPropagation;
      return this;
    }

    public Builder withBackwardsPropagation(BackwardsDelayPropagationType backwardsPropagation) {
      this.backwardsPropagation = backwardsPropagation;
      return this;
    }

    public Builder withAllowStopPatternModification(boolean allowStopPatternModification) {
      this.allowStopPatternModification = allowStopPatternModification;
      return this;
    }

    public TripUpdateOptions build() {
      return new TripUpdateOptions(
        forwardsPropagation,
        backwardsPropagation,
        allowStopPatternModification
      );
    }
  }
}
