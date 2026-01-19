package org.opentripplanner.updater.trip.model;

/**
 * The type of trip update operation.
 * <p>
 * Maps update semantics from both SIRI-ET and GTFS-RT formats:
 * <ul>
 *   <li>{@link #UPDATE_EXISTING} - Update times on existing trip (SIRI: trip update, GTFS-RT: SCHEDULED)</li>
 *   <li>{@link #CANCEL_TRIP} - Cancel entire trip (SIRI: Cancellation=true, GTFS-RT: CANCELED)</li>
 *   <li>{@link #DELETE_TRIP} - Delete trip (SIRI: Cancellation=true, GTFS-RT: DELETED)</li>
 *   <li>{@link #ADD_NEW_TRIP} - Add trip not in schedule (SIRI: REPLACEMENT_DEPARTURE, GTFS-RT: NEW/ADDED)</li>
 *   <li>{@link #MODIFY_TRIP} - Replace trip with modified pattern (SIRI: modified pattern, GTFS-RT: REPLACEMENT)</li>
 *   <li>{@link #ADD_EXTRA_CALLS} - Add stops to existing trip (SIRI-specific: EXTRA_CALL)</li>
 * </ul>
 */
public enum TripUpdateType {
  /**
   * Update arrival/departure times on an existing trip without changing the stop pattern.
   */
  UPDATE_EXISTING(false, false, false),

  /**
   * Cancel the entire trip. The trip is still visible but marked as cancelled.
   */
  CANCEL_TRIP(false, true, false),

  /**
   * Delete the trip. Stronger than cancel - the trip is removed from routing.
   */
  DELETE_TRIP(false, true, false),

  /**
   * Add a new trip that does not exist in the scheduled data.
   */
  ADD_NEW_TRIP(true, false, true),

  /**
   * Modify an existing trip by replacing it with a new version that has a different stop pattern.
   */
  MODIFY_TRIP(true, false, true),

  /**
   * Add extra stops (calls) to an existing trip. This is SIRI-specific and not directly
   * supported by GTFS-RT.
   */
  ADD_EXTRA_CALLS(false, false, true);

  private final boolean createsNewTrip;
  private final boolean removesTrip;
  private final boolean modifiesStopPattern;

  TripUpdateType(boolean createsNewTrip, boolean removesTrip, boolean modifiesStopPattern) {
    this.createsNewTrip = createsNewTrip;
    this.removesTrip = removesTrip;
    this.modifiesStopPattern = modifiesStopPattern;
  }

  /**
   * Returns true if this update type creates a new trip (either brand new or a replacement).
   */
  public boolean createsNewTrip() {
    return createsNewTrip;
  }

  /**
   * Returns true if this update type removes the trip from routing (cancel or delete).
   */
  public boolean removesTrip() {
    return removesTrip;
  }

  /**
   * Returns true if this update type modifies the stop pattern (adding/removing stops).
   */
  public boolean modifiesStopPattern() {
    return modifiesStopPattern;
  }
}
