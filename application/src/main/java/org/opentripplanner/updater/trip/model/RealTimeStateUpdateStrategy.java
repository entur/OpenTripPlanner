package org.opentripplanner.updater.trip.model;

/**
 * Strategy for determining the RealTimeState of updated TripTimes.
 * Different real-time feed types have different expectations for when
 * TripTimes should be marked as MODIFIED vs UPDATED.
 */
public enum RealTimeStateUpdateStrategy {
  /**
   * Always set TripTimes.getRealTimeState() to UPDATED, even when the trip moves to a modified pattern.
   * Used for GTFS-RT feeds to match legacy behavior (TripTimesUpdater:222).
   */
  ALWAYS_UPDATED,

  /**
   * Set TripTimes.getRealTimeState() to MODIFIED when the trip moves to a modified pattern,
   * UPDATED when it stays on the original pattern.
   * Used for SIRI-ET feeds to match legacy behavior (ModifiedTripBuilder:145-151).
   */
  MODIFIED_ON_PATTERN_CHANGE,
}
