package org.opentripplanner.ext.updater.trip.unified.policy;

/**
 * Whether the calls of a newly built stop pattern are marked as GTFS timepoints - stops whose
 * times are exact rather than interpolated. Each format binds the matching policy constant once at
 * the boundary (see {@link FormatPolicy}).
 */
public interface TimepointPolicy {
  boolean callsAreTimepoints();

  /**
   * GTFS-RT: every call of a created trip carries an exact time, so it is a timepoint. Legacy marks
   * the same calls, in {@code TripTimesUpdater}.
   */
  TimepointPolicy EXACT_TIMES = () -> true;

  /**
   * SIRI-ET: a call of a created trip is left unmarked, the way the legacy
   * {@code StopTimesMapper} leaves it.
   */
  TimepointPolicy UNMARKED = () -> false;
}
