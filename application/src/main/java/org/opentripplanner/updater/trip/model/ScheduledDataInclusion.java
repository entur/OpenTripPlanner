package org.opentripplanner.updater.trip.model;

/**
 * Controls whether scheduled (aimed) data should be included when creating patterns
 * for added trips.
 *
 * <p>This is separate from {@link StopUpdateStrategy} which controls how stop updates
 * are matched to stops in a pattern.
 */
public enum ScheduledDataInclusion {
  /**
   * Include scheduled trip times in the pattern for added trips.
   * This enables querying aimed/scheduled times for the added trip.
   * Used by SIRI-ET.
   */
  INCLUDE,

  /**
   * Do not include scheduled trip times in the pattern for added trips.
   * Only real-time data is available.
   * Used by GTFS-RT.
   */
  EXCLUDE,
}
