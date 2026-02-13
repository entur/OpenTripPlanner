package org.opentripplanner.updater.trip.model;

/**
 * Controls the {@link org.opentripplanner.transit.model.timetable.RealTimeState}
 * when re-updating an already-added trip.
 *
 * <p>SIRI-ET treats a subsequent update to an extra journey as a modification, so the
 * state transitions to UPDATED. GTFS-RT keeps the trip as ADDED since it was never
 * part of the static schedule.
 */
public enum AddedTripUpdateState {
  /**
   * Keep {@code RealTimeState.ADDED} when re-updating an added trip.
   * Used by GTFS-RT.
   */
  RETAIN_ADDED,

  /**
   * Set {@code RealTimeState.UPDATED} when re-updating an added trip.
   * Used by SIRI-ET.
   */
  SET_UPDATED,
}
