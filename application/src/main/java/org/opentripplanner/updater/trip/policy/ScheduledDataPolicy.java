package org.opentripplanner.updater.trip.policy;

/**
 * Whether scheduled trip times are included in the pattern of an added trip, so aimed times are
 * queryable. Replaces the format-divergent {@code ScheduledDataInclusion} enum.
 */
public interface ScheduledDataPolicy {
  boolean includesScheduledData();

  /** SIRI-ET: include scheduled times. */
  ScheduledDataPolicy INCLUDE = () -> true;
  /** GTFS-RT: do not include scheduled times. */
  ScheduledDataPolicy EXCLUDE = () -> false;
}
