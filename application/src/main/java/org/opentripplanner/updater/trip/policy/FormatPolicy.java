package org.opentripplanner.updater.trip.policy;

import org.opentripplanner.updater.trip.gtfs.interpolation.BackwardsDelayPropagationType;
import org.opentripplanner.updater.trip.gtfs.interpolation.ForwardsDelayPropagationType;

/**
 * Immutable bundle of the behavioural policies that capture how a real-time message of a given
 * format is applied. The format is chosen <em>once</em>, at the parser boundary
 * ({@link #siri()} / {@link #gtfsRt}), and downstream code asks the policy for behaviour
 * ({@code policy.pickDrop().effective(...)}) instead of reading a format flag or enum.
 */
public record FormatPolicy(
  PickDropPolicy pickDrop,
  RealTimeStatePolicy realTimeState,
  StopMatchingPolicy stopMatching,
  StopReplacementPolicy stopReplacement,
  DelayPropagationPolicy delayPropagation,
  FirstLastStopTimePolicy firstLastStopTime,
  ScheduledDataPolicy scheduledData,
  UnknownStopPolicy unknownStop
) {
  /** The SIRI-ET format policy. */
  public static FormatPolicy siri() {
    return new FormatPolicy(
      PickDropPolicy.ROUTABILITY_CHANGE_ONLY,
      RealTimeStatePolicy.MODIFIED_ON_PATTERN_CHANGE,
      StopMatchingPolicy.POSITIONAL,
      StopReplacementPolicy.SAME_PARENT_STATION,
      DelayPropagationPolicy.of(
        ForwardsDelayPropagationType.NONE,
        BackwardsDelayPropagationType.NONE
      ),
      FirstLastStopTimePolicy.ADJUST,
      ScheduledDataPolicy.INCLUDE,
      UnknownStopPolicy.FAIL
    );
  }

  /** The GTFS-RT format policy, parameterized by the configured delay propagation. */
  public static FormatPolicy gtfsRt(
    ForwardsDelayPropagationType forwardsPropagation,
    BackwardsDelayPropagationType backwardsPropagation
  ) {
    return new FormatPolicy(
      PickDropPolicy.EXACT_MATCH,
      RealTimeStatePolicy.ALWAYS_UPDATED,
      StopMatchingPolicy.BY_SEQUENCE_OR_ID,
      StopReplacementPolicy.ANY_STOP,
      DelayPropagationPolicy.of(forwardsPropagation, backwardsPropagation),
      FirstLastStopTimePolicy.PRESERVE,
      ScheduledDataPolicy.EXCLUDE,
      UnknownStopPolicy.IGNORE
    );
  }

  /** Whether this format propagates delays (forward or backward). */
  public boolean propagatesDelays() {
    return delayPropagation.propagatesDelays();
  }

  public static Builder builder() {
    return new Builder();
  }

  /**
   * Builder for custom policy combinations. The defaults match the previous GTFS-RT-flavoured
   * {@code TripUpdateOptions.Builder} defaults; override individual axes as needed (mainly in tests).
   */
  public static final class Builder {

    private PickDropPolicy pickDrop = PickDropPolicy.EXACT_MATCH;
    private RealTimeStatePolicy realTimeState = RealTimeStatePolicy.ALWAYS_UPDATED;
    private StopMatchingPolicy stopMatching = StopMatchingPolicy.BY_SEQUENCE_OR_ID;
    private StopReplacementPolicy stopReplacement = StopReplacementPolicy.ANY_STOP;
    private DelayPropagationPolicy delayPropagation = DelayPropagationPolicy.of(
      ForwardsDelayPropagationType.NONE,
      BackwardsDelayPropagationType.NONE
    );
    private FirstLastStopTimePolicy firstLastStopTime = FirstLastStopTimePolicy.PRESERVE;
    private ScheduledDataPolicy scheduledData = ScheduledDataPolicy.EXCLUDE;
    private UnknownStopPolicy unknownStop = UnknownStopPolicy.IGNORE;

    public Builder withPickDrop(PickDropPolicy pickDrop) {
      this.pickDrop = pickDrop;
      return this;
    }

    public Builder withRealTimeState(RealTimeStatePolicy realTimeState) {
      this.realTimeState = realTimeState;
      return this;
    }

    public Builder withStopMatching(StopMatchingPolicy stopMatching) {
      this.stopMatching = stopMatching;
      return this;
    }

    public Builder withStopReplacement(StopReplacementPolicy stopReplacement) {
      this.stopReplacement = stopReplacement;
      return this;
    }

    public Builder withDelayPropagation(DelayPropagationPolicy delayPropagation) {
      this.delayPropagation = delayPropagation;
      return this;
    }

    public Builder withFirstLastStopTime(FirstLastStopTimePolicy firstLastStopTime) {
      this.firstLastStopTime = firstLastStopTime;
      return this;
    }

    public Builder withScheduledData(ScheduledDataPolicy scheduledData) {
      this.scheduledData = scheduledData;
      return this;
    }

    public Builder withUnknownStop(UnknownStopPolicy unknownStop) {
      this.unknownStop = unknownStop;
      return this;
    }

    public FormatPolicy build() {
      return new FormatPolicy(
        pickDrop,
        realTimeState,
        stopMatching,
        stopReplacement,
        delayPropagation,
        firstLastStopTime,
        scheduledData,
        unknownStop
      );
    }
  }
}
