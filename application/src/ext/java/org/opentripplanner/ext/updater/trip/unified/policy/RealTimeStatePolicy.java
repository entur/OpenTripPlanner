package org.opentripplanner.ext.updater.trip.unified.policy;

import org.opentripplanner.transit.model.timetable.RealTimeTripTimesBuilder;

/**
 * What a message states about the real-time state of the trip it revises, over and above the times
 * it applies. Each format binds the matching policy constant once at the boundary (see
 * {@link FormatPolicy}).
 * <p>
 * The times themselves are never this policy's business: every real-time time written to the builder
 * already marks the trip updated. What is left is what the formats disagree about - whether
 * receiving a message is news in itself, and whether a changed pattern is reported as MODIFIED.
 */
public interface RealTimeStatePolicy {
  /**
   * Mark whatever the message states about the trip's real-time state.
   *
   * @param patternChanged whether the resulting stop pattern differs from the scheduled one
   */
  void mark(RealTimeTripTimesBuilder builder, boolean patternChanged);

  /**
   * GTFS-RT: a message about a trip makes it real-time updated whatever it says, and a changed
   * pattern is reported as UPDATED rather than MODIFIED. Legacy says the same, unconditionally, in
   * {@code TripTimesUpdater}.
   */
  RealTimeStatePolicy ALWAYS_UPDATED = (builder, patternChanged) -> builder.withRealTimeUpdated();

  /**
   * SIRI-ET: the message states the pattern the journey runs and the times it predicts, and nothing
   * else - so a journey that predicts no time and changes no pattern leaves the trip scheduled.
   * Legacy says the same, in {@code ModifiedTripBuilder}, which marks a modified pattern and never
   * declares a trip updated on its own.
   */
  RealTimeStatePolicy MODIFIED_ON_PATTERN_CHANGE = (builder, patternChanged) -> {
    if (patternChanged) {
      builder.withModifiedTripPattern();
    }
  };
}
