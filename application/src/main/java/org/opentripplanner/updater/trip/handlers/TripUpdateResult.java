package org.opentripplanner.updater.trip.handlers;

import java.util.List;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.timetable.RealTimeTripUpdate;
import org.opentripplanner.transit.model.timetable.TripTimes;
import org.opentripplanner.updater.spi.UpdateSuccess;

/**
 * Result of handling a trip update, containing the real-time trip update
 * and any warnings that occurred during processing.
 *
 * <p>This keeps warnings separate from {@link RealTimeTripUpdate} so that
 * the core domain class doesn't need to know about update warnings.
 * Warnings are added to {@link UpdateSuccess} by the adapter after
 * calling the snapshot manager.
 */
public record TripUpdateResult(
  RealTimeTripUpdate realTimeTripUpdate,
  List<UpdateSuccess.WarningType> warnings
) {
  public TripUpdateResult {
    if (warnings == null) {
      warnings = List.of();
    }
  }

  /**
   * Create a result with no warnings.
   */
  public TripUpdateResult(RealTimeTripUpdate realTimeTripUpdate) {
    this(realTimeTripUpdate, List.of());
  }

  /**
   * Convenience method to get the updated trip times from the contained RealTimeTripUpdate.
   */
  public TripTimes updatedTripTimes() {
    return realTimeTripUpdate.updatedTripTimes();
  }

  /**
   * Convenience method to get the pattern from the contained RealTimeTripUpdate.
   */
  public TripPattern pattern() {
    return realTimeTripUpdate.pattern();
  }
}
