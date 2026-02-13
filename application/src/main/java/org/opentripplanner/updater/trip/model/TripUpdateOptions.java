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
  private final ScheduledDataInclusion scheduledDataInclusion;
  private final UnknownStopBehavior unknownStopBehavior;
  private final AddedTripUpdateState addedTripUpdateState;

  /**
   * @param forwardsPropagation How delays should be propagated to future stops
   * @param backwardsPropagation How delays should be propagated to past stops
   * @param stopReplacementConstraint Constraint on which stops can replace scheduled stops
   * @param stopUpdateStrategy Strategy for matching stop updates to stops in the pattern
   * @param realTimeStateStrategy Strategy for determining RealTimeState of updated TripTimes
   * @param firstLastStopTimeAdjustment Strategy for adjusting times at first/last stops
   * @param scheduledDataInclusion Whether to include scheduled data for added trips
   * @param unknownStopBehavior Behavior when encountering unknown stops in added trips
   * @param addedTripUpdateState RealTimeState to use when re-updating an already-added trip
   */
  public TripUpdateOptions(
    ForwardsDelayPropagationType forwardsPropagation,
    BackwardsDelayPropagationType backwardsPropagation,
    StopReplacementConstraint stopReplacementConstraint,
    StopUpdateStrategy stopUpdateStrategy,
    RealTimeStateUpdateStrategy realTimeStateStrategy,
    FirstLastStopTimeAdjustment firstLastStopTimeAdjustment,
    ScheduledDataInclusion scheduledDataInclusion,
    UnknownStopBehavior unknownStopBehavior,
    AddedTripUpdateState addedTripUpdateState
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
    this.scheduledDataInclusion = Objects.requireNonNull(
      scheduledDataInclusion,
      "scheduledDataInclusion must not be null"
    );
    this.unknownStopBehavior = Objects.requireNonNull(
      unknownStopBehavior,
      "unknownStopBehavior must not be null"
    );
    this.addedTripUpdateState = Objects.requireNonNull(
      addedTripUpdateState,
      "addedTripUpdateState must not be null"
    );
  }

  /**
   * Returns default options for SIRI-ET updates.
   * SIRI provides explicit times for all stops, so no delay interpolation is needed.
   * Stop replacements are constrained to the same parent station (quay/platform changes).
   * First/last stop times are adjusted to avoid negative dwell times.
   * Scheduled data is included so aimed times are queryable.
   * Unknown stops cause the update to fail.
   */
  public static TripUpdateOptions siriDefaults() {
    return new TripUpdateOptions(
      ForwardsDelayPropagationType.NONE,
      BackwardsDelayPropagationType.NONE,
      StopReplacementConstraint.SAME_PARENT_STATION,
      StopUpdateStrategy.FULL_UPDATE,
      RealTimeStateUpdateStrategy.MODIFIED_ON_PATTERN_CHANGE,
      FirstLastStopTimeAdjustment.ADJUST,
      ScheduledDataInclusion.INCLUDE,
      UnknownStopBehavior.FAIL,
      AddedTripUpdateState.SET_UPDATED
    );
  }

  /**
   * Returns default options for GTFS-RT updates.
   * GTFS-RT may need delay interpolation for stops without explicit times.
   * Stop replacements have no constraints (replacement trips can use any stops).
   * First/last stop times are preserved as provided in the message.
   * Scheduled data is not included for added trips.
   * Unknown stops are silently ignored.
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
      FirstLastStopTimeAdjustment.PRESERVE,
      ScheduledDataInclusion.EXCLUDE,
      UnknownStopBehavior.IGNORE,
      AddedTripUpdateState.RETAIN_ADDED
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

  public ScheduledDataInclusion scheduledDataInclusion() {
    return scheduledDataInclusion;
  }

  public UnknownStopBehavior unknownStopBehavior() {
    return unknownStopBehavior;
  }

  public AddedTripUpdateState addedTripUpdateState() {
    return addedTripUpdateState;
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
      firstLastStopTimeAdjustment == that.firstLastStopTimeAdjustment &&
      scheduledDataInclusion == that.scheduledDataInclusion &&
      unknownStopBehavior == that.unknownStopBehavior &&
      addedTripUpdateState == that.addedTripUpdateState
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
      firstLastStopTimeAdjustment,
      scheduledDataInclusion,
      unknownStopBehavior,
      addedTripUpdateState
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
      ", scheduledDataInclusion=" +
      scheduledDataInclusion +
      ", unknownStopBehavior=" +
      unknownStopBehavior +
      ", addedTripUpdateState=" +
      addedTripUpdateState +
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
    private ScheduledDataInclusion scheduledDataInclusion = ScheduledDataInclusion.EXCLUDE;
    private UnknownStopBehavior unknownStopBehavior = UnknownStopBehavior.IGNORE;
    private AddedTripUpdateState addedTripUpdateState = AddedTripUpdateState.RETAIN_ADDED;

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

    public Builder withScheduledDataInclusion(ScheduledDataInclusion scheduledDataInclusion) {
      this.scheduledDataInclusion = scheduledDataInclusion;
      return this;
    }

    public Builder withUnknownStopBehavior(UnknownStopBehavior unknownStopBehavior) {
      this.unknownStopBehavior = unknownStopBehavior;
      return this;
    }

    public Builder withAddedTripUpdateState(AddedTripUpdateState addedTripUpdateState) {
      this.addedTripUpdateState = addedTripUpdateState;
      return this;
    }

    public TripUpdateOptions build() {
      return new TripUpdateOptions(
        forwardsPropagation,
        backwardsPropagation,
        stopReplacementConstraint,
        stopUpdateStrategy,
        realTimeStateStrategy,
        firstLastStopTimeAdjustment,
        scheduledDataInclusion,
        unknownStopBehavior,
        addedTripUpdateState
      );
    }
  }
}
