package org.opentripplanner.ext.updater.trip.unified.policy;

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
  UnreportedTimePolicy unreportedTime,
  ScheduledDataPolicy scheduledData,
  UnknownStopPolicy unknownStop,
  ImplicitCancellationPolicy implicitCancellation,
  RepeatedAdditionPolicy repeatedAddition,
  TimepointPolicy timepoint
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
      UnreportedTimePolicy.AIMED_TIME,
      ScheduledDataPolicy.INCLUDE,
      UnknownStopPolicy.FAIL,
      ImplicitCancellationPolicy.NOTHING_ROUTABLE,
      RepeatedAdditionPolicy.REVISE_IN_PLACE,
      TimepointPolicy.UNMARKED
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
      UnreportedTimePolicy.NO_TIME,
      ScheduledDataPolicy.EXCLUDE,
      UnknownStopPolicy.IGNORE,
      ImplicitCancellationPolicy.NEVER,
      RepeatedAdditionPolicy.REBUILD_FROM_CALLS,
      TimepointPolicy.EXACT_TIMES
    );
  }

  public static Builder builder() {
    return new Builder();
  }

  /**
   * Builder for custom policy combinations, used by tests that need one axis to differ from the
   * GTFS-RT-flavoured defaults. Only the axes with a {@code with*} method can be overridden - add
   * one for the others if a test needs to vary them.
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
    private UnreportedTimePolicy unreportedTime = UnreportedTimePolicy.NO_TIME;
    private ScheduledDataPolicy scheduledData = ScheduledDataPolicy.EXCLUDE;
    private UnknownStopPolicy unknownStop = UnknownStopPolicy.IGNORE;
    private ImplicitCancellationPolicy implicitCancellation = ImplicitCancellationPolicy.NEVER;
    private RepeatedAdditionPolicy repeatedAddition = RepeatedAdditionPolicy.REBUILD_FROM_CALLS;
    private TimepointPolicy timepoint = TimepointPolicy.EXACT_TIMES;

    public Builder withStopMatching(StopMatchingPolicy stopMatching) {
      this.stopMatching = stopMatching;
      return this;
    }

    public Builder withStopReplacement(StopReplacementPolicy stopReplacement) {
      this.stopReplacement = stopReplacement;
      return this;
    }

    public Builder withUnknownStop(UnknownStopPolicy unknownStop) {
      this.unknownStop = unknownStop;
      return this;
    }

    public Builder withUnreportedTime(UnreportedTimePolicy unreportedTime) {
      this.unreportedTime = unreportedTime;
      return this;
    }

    public FormatPolicy build() {
      return new FormatPolicy(
        pickDrop,
        realTimeState,
        stopMatching,
        stopReplacement,
        delayPropagation,
        unreportedTime,
        scheduledData,
        unknownStop,
        implicitCancellation,
        repeatedAddition,
        timepoint
      );
    }
  }
}
