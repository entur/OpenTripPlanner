package org.opentripplanner.updater.trip.siri;

/**
 * Types of SIRI update messages.
 */
public enum SiriUpdateType {
  /**
   * Update of an existing trip.
   * This can be either a trip defined in planned data or a replacement departure
   * that was previously added by a real-time message.
   * The update can consist in updated passing times and/or cancellation of some stops.
   * A stop can be substituted by another if they belong to the same station.
   * The whole trip can also be marked as cancelled.
   */
  TRIP_UPDATE,

  /**
   * Addition of a new trip, not currently present in the system.
   * The new trip has a new unique id.
   * The trip can replace one or more existing trips, another SIRI message should handle the
   * cancellation of the replaced trips.
   */
  REPLACEMENT_DEPARTURE,

  /**
   * Addition of one or more stops in an existing trip.
   */
  EXTRA_CALL,
}
