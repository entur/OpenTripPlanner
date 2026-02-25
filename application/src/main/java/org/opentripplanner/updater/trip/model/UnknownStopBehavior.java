package org.opentripplanner.updater.trip.model;

/**
 * Defines behavior when an unknown stop is encountered in a trip update for an added trip.
 *
 * <p>This is separate from {@link StopReplacementConstraint} which controls constraints
 * on which stops can replace scheduled stops in existing trips.
 */
public enum UnknownStopBehavior {
  /**
   * Fail the update if any stop is unknown.
   * Used by SIRI-ET where all stops must be valid.
   */
  FAIL,

  /**
   * Ignore (filter out) unknown stops and continue processing.
   * A warning is added to the result.
   * Used by GTFS-RT where unknown stops are silently removed.
   */
  IGNORE,
}
