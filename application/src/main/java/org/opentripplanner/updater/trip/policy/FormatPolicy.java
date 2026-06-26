package org.opentripplanner.updater.trip.policy;

import org.opentripplanner.updater.trip.gtfs.BackwardsDelayPropagationType;
import org.opentripplanner.updater.trip.gtfs.ForwardsDelayPropagationType;
import org.opentripplanner.updater.trip.model.TripUpdateOptions;

/**
 * Immutable bundle of the behavioural policies that capture how a real-time message of a given
 * format is applied. The format is chosen <em>once</em>, at the parser boundary, and downstream
 * code asks the policy for behaviour ({@code policy.pickDrop().effective(...)}) instead of reading
 * a format flag or enum.
 * <p>
 * During the incremental migration ({@code #7220}) {@code FormatPolicy} wraps the existing
 * {@link TripUpdateOptions} and derives each policy from it, so the composition is provably
 * behaviour-identical to the enum bag while consumers are migrated policy-by-policy. The wrapped
 * {@link #options()} stays available for the not-yet-migrated axes.
 */
public record FormatPolicy(TripUpdateOptions options) {
  public static FormatPolicy fromOptions(TripUpdateOptions options) {
    return new FormatPolicy(options);
  }

  /** The SIRI-ET format policy. */
  public static FormatPolicy siri() {
    return fromOptions(TripUpdateOptions.siriDefaults());
  }

  /** The GTFS-RT format policy, parameterized by the configured delay propagation. */
  public static FormatPolicy gtfsRt(
    ForwardsDelayPropagationType forwardsPropagation,
    BackwardsDelayPropagationType backwardsPropagation
  ) {
    return fromOptions(
      TripUpdateOptions.gtfsRtDefaults(forwardsPropagation, backwardsPropagation)
    );
  }

  public PickDropPolicy pickDrop() {
    return switch (options.pickDropChangeStrategy()) {
      case EXACT_MATCH -> PickDropPolicy.EXACT_MATCH;
      case ROUTABILITY_CHANGE_ONLY -> PickDropPolicy.ROUTABILITY_CHANGE_ONLY;
    };
  }

  public RealTimeStatePolicy realTimeState() {
    return switch (options.realTimeStateStrategy()) {
      case ALWAYS_UPDATED -> RealTimeStatePolicy.ALWAYS_UPDATED;
      case MODIFIED_ON_PATTERN_CHANGE -> RealTimeStatePolicy.MODIFIED_ON_PATTERN_CHANGE;
    };
  }

  public StopMatchingPolicy stopMatching() {
    return switch (options.stopUpdateStrategy()) {
      case FULL_UPDATE -> StopMatchingPolicy.POSITIONAL;
      case PARTIAL_UPDATE -> StopMatchingPolicy.BY_SEQUENCE_OR_ID;
    };
  }

  public StopReplacementPolicy stopReplacement() {
    return switch (options.stopReplacementConstraint()) {
      case ANY_STOP -> StopReplacementPolicy.ANY_STOP;
      case SAME_PARENT_STATION -> StopReplacementPolicy.SAME_PARENT_STATION;
      case NOT_ALLOWED -> StopReplacementPolicy.NOT_ALLOWED;
    };
  }

  /** Whether this format propagates delays (forward or backward). */
  public boolean propagatesDelays() {
    return options.propagatesDelays();
  }
}
