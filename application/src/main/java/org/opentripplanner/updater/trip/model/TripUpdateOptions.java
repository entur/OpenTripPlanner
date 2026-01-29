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
  private final StopReplacementConstraint stopReplacementConstraint;
  private final StopUpdateStrategy stopUpdateStrategy;
  private final StopCancellationTrackingStrategy stopCancellationTracking;

  /**
   * @param forwardsPropagation How delays should be propagated to future stops
   * @param backwardsPropagation How delays should be propagated to past stops
   * @param allowStopPatternModification Whether stop pattern modifications are allowed
   * @param stopReplacementConstraint Constraint on which stops can replace scheduled stops
   * @param stopUpdateStrategy Strategy for matching stop updates to stops in the pattern
   * @param stopCancellationTracking Strategy for tracking cancelled stops
   */
  public TripUpdateOptions(
    ForwardsDelayPropagationType forwardsPropagation,
    BackwardsDelayPropagationType backwardsPropagation,
    boolean allowStopPatternModification,
    StopReplacementConstraint stopReplacementConstraint,
    StopUpdateStrategy stopUpdateStrategy,
    StopCancellationTrackingStrategy stopCancellationTracking
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
    this.stopReplacementConstraint = Objects.requireNonNull(
      stopReplacementConstraint,
      "stopReplacementConstraint must not be null"
    );
    this.stopUpdateStrategy = Objects.requireNonNull(
      stopUpdateStrategy,
      "stopUpdateStrategy must not be null"
    );
    this.stopCancellationTracking = Objects.requireNonNull(
      stopCancellationTracking,
      "stopCancellationTracking must not be null"
    );
  }

  /**
   * Returns default options for SIRI-ET updates.
   * SIRI provides explicit times for all stops, so no delay interpolation is needed.
   * Stop replacements are constrained to the same parent station (quay/platform changes).
   */
  public static TripUpdateOptions siriDefaults() {
    return new TripUpdateOptions(
      ForwardsDelayPropagationType.NONE,
      BackwardsDelayPropagationType.NONE,
      true,
      StopReplacementConstraint.SAME_PARENT_STATION,
      StopUpdateStrategy.FULL_UPDATE,
      StopCancellationTrackingStrategy.NO_TRACK
    );
  }

  /**
   * Returns default options for GTFS-RT updates.
   * GTFS-RT may need delay interpolation for stops without explicit times.
   * Stop replacements have no constraints (replacement trips can use any stops).
   */
  public static TripUpdateOptions gtfsRtDefaults(
    ForwardsDelayPropagationType forwardsPropagation,
    BackwardsDelayPropagationType backwardsPropagation
  ) {
    return new TripUpdateOptions(
      forwardsPropagation,
      backwardsPropagation,
      true,
      StopReplacementConstraint.ANY_STOP,
      StopUpdateStrategy.PARTIAL_UPDATE,
      StopCancellationTrackingStrategy.TRACK_AS_PICKUP_DROPOFF_CHANGE
    );
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

  public StopReplacementConstraint stopReplacementConstraint() {
    return stopReplacementConstraint;
  }

  public StopUpdateStrategy stopUpdateStrategy() {
    return stopUpdateStrategy;
  }

  public StopCancellationTrackingStrategy stopCancellationTracking() {
    return stopCancellationTracking;
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
      backwardsPropagation == that.backwardsPropagation &&
      stopReplacementConstraint == that.stopReplacementConstraint &&
      stopUpdateStrategy == that.stopUpdateStrategy &&
      stopCancellationTracking == that.stopCancellationTracking
    );
  }

  @Override
  public int hashCode() {
    return Objects.hash(
      forwardsPropagation,
      backwardsPropagation,
      allowStopPatternModification,
      stopReplacementConstraint,
      stopUpdateStrategy,
      stopCancellationTracking
    );
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
      ", stopReplacementConstraint=" +
      stopReplacementConstraint +
      ", stopUpdateStrategy=" +
      stopUpdateStrategy +
      ", stopCancellationTracking=" +
      stopCancellationTracking +
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
    private StopReplacementConstraint stopReplacementConstraint =
      StopReplacementConstraint.ANY_STOP;
    private StopUpdateStrategy stopUpdateStrategy = StopUpdateStrategy.PARTIAL_UPDATE;
    private StopCancellationTrackingStrategy stopCancellationTracking =
      StopCancellationTrackingStrategy.NO_TRACK;

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

    public Builder withStopReplacementConstraint(
      StopReplacementConstraint stopReplacementConstraint
    ) {
      this.stopReplacementConstraint = stopReplacementConstraint;
      return this;
    }

    public Builder withStopUpdateStrategy(StopUpdateStrategy stopUpdateStrategy) {
      this.stopUpdateStrategy = stopUpdateStrategy;
      return this;
    }

    public Builder withStopCancellationTracking(
      StopCancellationTrackingStrategy stopCancellationTracking
    ) {
      this.stopCancellationTracking = stopCancellationTracking;
      return this;
    }

    public TripUpdateOptions build() {
      return new TripUpdateOptions(
        forwardsPropagation,
        backwardsPropagation,
        allowStopPatternModification,
        stopReplacementConstraint,
        stopUpdateStrategy,
        stopCancellationTracking
      );
    }
  }
}
