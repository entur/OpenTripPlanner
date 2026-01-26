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
 *   <li>{@link #MODIFY_TRIP} - Modify stop pattern (SIRI: EXTRA_CALL, GTFS-RT: REPLACEMENT)</li>
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
   * Modify an existing trip by changing its stop pattern. This covers:
   * <ul>
   *   <li>SIRI-ET extra calls: Adding stops to an existing trip (stops marked with
   *       {@code ParsedStopTimeUpdate.isExtraCall=true})</li>
   *   <li>GTFS-RT REPLACEMENT: Completely replacing the stop pattern of an existing trip</li>
   * </ul>
   * The difference is an implementation detail - SIRI has constraints (insertions only)
   * while GTFS-RT allows full replacement.
   */
  MODIFY_TRIP(true, false, true);

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
