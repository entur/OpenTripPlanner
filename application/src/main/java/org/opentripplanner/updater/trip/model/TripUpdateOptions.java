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
  private final StopReplacementConstraint stopReplacementConstraint;
  private final StopUpdateStrategy stopUpdateStrategy;
  private final RealTimeStateUpdateStrategy realTimeStateStrategy;
  private final FirstLastStopTimeAdjustment firstLastStopTimeAdjustment;

  /**
   * @param forwardsPropagation How delays should be propagated to future stops
   * @param backwardsPropagation How delays should be propagated to past stops
   * @param stopReplacementConstraint Constraint on which stops can replace scheduled stops
   * @param stopUpdateStrategy Strategy for matching stop updates to stops in the pattern
   * @param realTimeStateStrategy Strategy for determining RealTimeState of updated TripTimes
   * @param firstLastStopTimeAdjustment Strategy for adjusting times at first/last stops
   */
  public TripUpdateOptions(
    ForwardsDelayPropagationType forwardsPropagation,
    BackwardsDelayPropagationType backwardsPropagation,
    StopReplacementConstraint stopReplacementConstraint,
    StopUpdateStrategy stopUpdateStrategy,
    RealTimeStateUpdateStrategy realTimeStateStrategy,
    FirstLastStopTimeAdjustment firstLastStopTimeAdjustment
  ) {
    this.forwardsPropagation = Objects.requireNonNull(
      forwardsPropagation,
      "forwardsPropagation must not be null"
    );
    this.backwardsPropagation = Objects.requireNonNull(
      backwardsPropagation,
      "backwardsPropagation must not be null"
    );
    this.stopReplacementConstraint = Objects.requireNonNull(
      stopReplacementConstraint,
      "stopReplacementConstraint must not be null"
    );
    this.stopUpdateStrategy = Objects.requireNonNull(
      stopUpdateStrategy,
      "stopUpdateStrategy must not be null"
    );
    this.realTimeStateStrategy = Objects.requireNonNull(
      realTimeStateStrategy,
      "realTimeStateStrategy must not be null"
    );
    this.firstLastStopTimeAdjustment = Objects.requireNonNull(
      firstLastStopTimeAdjustment,
      "firstLastStopTimeAdjustment must not be null"
    );
  }

  /**
   * Returns default options for SIRI-ET updates.
   * SIRI provides explicit times for all stops, so no delay interpolation is needed.
   * Stop replacements are constrained to the same parent station (quay/platform changes).
   * First/last stop times are adjusted to avoid negative dwell times.
   */
  public static TripUpdateOptions siriDefaults() {
    return new TripUpdateOptions(
      ForwardsDelayPropagationType.NONE,
      BackwardsDelayPropagationType.NONE,
      StopReplacementConstraint.SAME_PARENT_STATION,
      StopUpdateStrategy.FULL_UPDATE,
      RealTimeStateUpdateStrategy.MODIFIED_ON_PATTERN_CHANGE,
      FirstLastStopTimeAdjustment.ADJUST
    );
  }

  /**
   * Returns default options for GTFS-RT updates.
   * GTFS-RT may need delay interpolation for stops without explicit times.
   * Stop replacements have no constraints (replacement trips can use any stops).
   * First/last stop times are preserved as provided in the message.
   */
  public static TripUpdateOptions gtfsRtDefaults(
    ForwardsDelayPropagationType forwardsPropagation,
    BackwardsDelayPropagationType backwardsPropagation
  ) {
    return new TripUpdateOptions(
      forwardsPropagation,
      backwardsPropagation,
      StopReplacementConstraint.ANY_STOP,
      StopUpdateStrategy.PARTIAL_UPDATE,
      RealTimeStateUpdateStrategy.ALWAYS_UPDATED,
      FirstLastStopTimeAdjustment.PRESERVE
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

  public StopReplacementConstraint stopReplacementConstraint() {
    return stopReplacementConstraint;
  }

  public StopUpdateStrategy stopUpdateStrategy() {
    return stopUpdateStrategy;
  }

  public RealTimeStateUpdateStrategy realTimeStateStrategy() {
    return realTimeStateStrategy;
  }

  public FirstLastStopTimeAdjustment firstLastStopTimeAdjustment() {
    return firstLastStopTimeAdjustment;
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
      forwardsPropagation == that.forwardsPropagation &&
      backwardsPropagation == that.backwardsPropagation &&
      stopReplacementConstraint == that.stopReplacementConstraint &&
      stopUpdateStrategy == that.stopUpdateStrategy &&
      realTimeStateStrategy == that.realTimeStateStrategy &&
      firstLastStopTimeAdjustment == that.firstLastStopTimeAdjustment
    );
  }

  @Override
  public int hashCode() {
    return Objects.hash(
      forwardsPropagation,
      backwardsPropagation,
      stopReplacementConstraint,
      stopUpdateStrategy,
      realTimeStateStrategy,
      firstLastStopTimeAdjustment
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
      ", stopReplacementConstraint=" +
      stopReplacementConstraint +
      ", stopUpdateStrategy=" +
      stopUpdateStrategy +
      ", realTimeStateStrategy=" +
      realTimeStateStrategy +
      ", firstLastStopTimeAdjustment=" +
      firstLastStopTimeAdjustment +
      '}'
    );
  }

  /**
   * Builder for TripUpdateOptions.
   */
  public static class Builder {

    private ForwardsDelayPropagationType forwardsPropagation = ForwardsDelayPropagationType.NONE;
    private BackwardsDelayPropagationType backwardsPropagation = BackwardsDelayPropagationType.NONE;
    private StopReplacementConstraint stopReplacementConstraint =
      StopReplacementConstraint.ANY_STOP;
    private StopUpdateStrategy stopUpdateStrategy = StopUpdateStrategy.PARTIAL_UPDATE;
    private RealTimeStateUpdateStrategy realTimeStateStrategy =
      RealTimeStateUpdateStrategy.ALWAYS_UPDATED;
    private FirstLastStopTimeAdjustment firstLastStopTimeAdjustment =
      FirstLastStopTimeAdjustment.PRESERVE;

    public Builder withForwardsPropagation(ForwardsDelayPropagationType forwardsPropagation) {
      this.forwardsPropagation = forwardsPropagation;
      return this;
    }

    public Builder withBackwardsPropagation(BackwardsDelayPropagationType backwardsPropagation) {
      this.backwardsPropagation = backwardsPropagation;
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

    public Builder withRealTimeStateStrategy(RealTimeStateUpdateStrategy realTimeStateStrategy) {
      this.realTimeStateStrategy = realTimeStateStrategy;
      return this;
    }

    public Builder withFirstLastStopTimeAdjustment(
      FirstLastStopTimeAdjustment firstLastStopTimeAdjustment
    ) {
      this.firstLastStopTimeAdjustment = firstLastStopTimeAdjustment;
      return this;
    }

    public TripUpdateOptions build() {
      return new TripUpdateOptions(
        forwardsPropagation,
        backwardsPropagation,
        stopReplacementConstraint,
        stopUpdateStrategy,
        realTimeStateStrategy,
        firstLastStopTimeAdjustment
      );
    }
  }
}
