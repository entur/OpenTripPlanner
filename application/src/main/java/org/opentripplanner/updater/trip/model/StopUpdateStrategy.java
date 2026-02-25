package org.opentripplanner.updater.trip.model;

/**
 * Strategy for how stop time updates should be matched to stops in a trip pattern.
 */
public enum StopUpdateStrategy {
  /**
   * Full update (SIRI-ET style):
   * - Position in stopTimeUpdates list corresponds to position in pattern
   * - All stops in the pattern must be present in the update
   * - stopSequence must NOT be used
   */
  FULL_UPDATE,

  /**
   * Partial update (GTFS-RT style):
   * - Stops can be matched by stopSequence OR stopId lookup
   * - Only affected stops need to be included
   * - Supports flexible stop matching
   */
  PARTIAL_UPDATE,
}
