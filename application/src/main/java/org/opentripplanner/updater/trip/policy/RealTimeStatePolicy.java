package org.opentripplanner.updater.trip.policy;

import org.opentripplanner.transit.model.timetable.RealTimeTripTimesBuilder;

/**
 * Marks the real-time state of an updated trip on the builder, given whether the stop pattern
 * changed. This replaces the format-divergent {@code RealTimeStateUpdateStrategy} enum branching:
 * each format binds the matching policy constant once at the boundary (see {@link FormatPolicy}).
 */
public interface RealTimeStatePolicy {
  /**
   * Mark the builder's real-time state.
   *
   * @param patternChanged whether the resulting stop pattern differs from the scheduled one
   */
  void mark(RealTimeTripTimesBuilder builder, boolean patternChanged);

  /** GTFS-RT: always marked UPDATED, even when the pattern changes. */
  RealTimeStatePolicy ALWAYS_UPDATED = (builder, patternChanged) -> builder.withRealTimeUpdated();

  /** SIRI-ET: MODIFIED when the pattern changes, UPDATED otherwise. */
  RealTimeStatePolicy MODIFIED_ON_PATTERN_CHANGE = (builder, patternChanged) -> {
    if (patternChanged) {
      builder.withModifiedTripPattern();
    } else {
      builder.withRealTimeUpdated();
    }
  };
}
