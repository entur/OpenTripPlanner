package org.opentripplanner.updater.trip.model;

import org.opentripplanner.transit.model.timetable.RealTimeTripTimesBuilder;

/**
 * Controls the real-time state when re-updating an already-added trip.
 *
 * <p>SIRI-ET treats a subsequent update to an extra journey as a modification, so the
 * state transitions to UPDATED (times modified, not added). GTFS-RT keeps the trip as ADDED
 * since it was never part of the static schedule.
 */
public enum AddedTripUpdateState {
  /**
   * Keep the trip as ADDED when re-updating.
   * Used by GTFS-RT.
   */
  RETAIN_ADDED,

  /**
   * Set the trip as UPDATED (times modified) when re-updating an added trip.
   * Used by SIRI-ET.
   */
  SET_UPDATED;

  /**
   * Apply the corresponding real-time state to the builder.
   */
  public void applyTo(RealTimeTripTimesBuilder builder) {
    switch (this) {
      case RETAIN_ADDED -> builder.withAdded();
      case SET_UPDATED ->
        // Extra journeys remain "added" trips even when their times are updated,
        // because they were never part of the static schedule.
        builder.withAdded();
    }
  }
}
