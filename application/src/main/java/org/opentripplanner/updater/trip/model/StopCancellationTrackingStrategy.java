package org.opentripplanner.updater.trip.model;

/**
 * Strategy for tracking pickup/dropoff changes when stops are cancelled.
 * Different real-time feed types have different expectations for how
 * cancelled stops affect trip pattern modifications.
 */
public enum StopCancellationTrackingStrategy {
  /**
   * Track cancelled stops as pickup/dropoff changes (PickDrop.CANCELLED).
   * This causes hasPatternChanges() to return true and moves the trip to a MODIFIED pattern.
   * Used for GTFS-RT feeds to match legacy behavior.
   */
  TRACK_AS_PICKUP_DROPOFF_CHANGE,

  /**
   * Do not track cancelled stops as pickup/dropoff changes.
   * The stop is still marked as cancelled in TripTimes via withCanceled(),
   * but this doesn't trigger pattern modification. The trip remains UPDATED.
   * Used for SIRI-ET feeds to match legacy behavior.
   */
  NO_TRACK,
}
