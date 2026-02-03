package org.opentripplanner.updater.trip.handlers;

import java.time.LocalDate;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.timetable.RealTimeTripUpdate;
import org.opentripplanner.transit.model.timetable.Trip;
import org.opentripplanner.updater.trip.TimetableSnapshotManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility methods shared between trip update handlers.
 */
public final class HandlerUtils {

  private static final Logger LOG = LoggerFactory.getLogger(HandlerUtils.class);

  private HandlerUtils() {}

  /**
   * Mark the scheduled trip in the buffer as deleted when moving to a modified pattern.
   * This prevents the trip from appearing in both the scheduled and modified patterns.
   *
   * @param trip The trip to delete from the scheduled pattern
   * @param scheduledPattern The scheduled pattern containing the trip
   * @param serviceDate The service date
   * @param snapshotManager The snapshot manager to update
   */
  public static void markScheduledTripAsDeleted(
    Trip trip,
    TripPattern scheduledPattern,
    LocalDate serviceDate,
    TimetableSnapshotManager snapshotManager
  ) {
    if (snapshotManager == null) {
      LOG.debug("No snapshot manager provided, skipping deletion of scheduled trip");
      return;
    }

    // Get the scheduled trip times from the scheduled timetable
    final var scheduledTimetable = scheduledPattern.getScheduledTimetable();
    final var scheduledTripTimes = scheduledTimetable.getTripTimes(trip);

    if (scheduledTripTimes == null) {
      LOG.warn("Could not mark scheduled trip as deleted: {}", trip.getId());
      return;
    }

    // Create a deleted version of the trip times
    final var builder = scheduledTripTimes.createRealTimeFromScheduledTimes();
    builder.deleteTrip();

    // Update the buffer with the deleted trip times in the scheduled pattern
    snapshotManager.updateBuffer(
      new RealTimeTripUpdate(scheduledPattern, builder.build(), serviceDate)
    );

    LOG.debug("Marked scheduled trip {} as deleted on {}", trip.getId(), serviceDate);
  }
}
