package org.opentripplanner.updater.trip;

import org.opentripplanner.transit.model.timetable.RealTimeTripTimesBuilder;
import org.opentripplanner.updater.trip.model.TripRemoval;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Removes a trip from the timetable on one service date. The two concrete removal operations are
 * {@link TripCanceller} (GTFS-RT CANCELED, SIRI-ET cancellation) and {@link TripDeleter}
 * (GTFS-RT DELETED); they differ only in the real-time state the trip ends up in, which is what
 * {@link #applyRemoval} contributes.
 * <p>
 * The update arrives already resolved to a trip in the transit model by the
 * {@link TripRemovalFactory} - a scheduled trip or one added by an earlier real-time message - and
 * applies itself through {@link TripRemoval#apply}. This class only supplies the removal
 * and logs the outcome.
 */
public abstract sealed class TripRemover permits TripCanceller, TripDeleter {

  private static final Logger LOG = LoggerFactory.getLogger(TripRemover.class);

  final TripUpdateResult remove(TripRemoval removal) {
    var result = removal.apply(this::applyRemoval);
    LOG.debug("{} trip {} on {}", getLogAction(), removal.tripId(), removal.serviceDate());
    return result;
  }

  /**
   * Apply the specific removal operation to the trip times builder.
   * Subclasses implement this to call either cancelTrip() or deleteTrip().
   */
  protected abstract void applyRemoval(RealTimeTripTimesBuilder builder);

  /**
   * Get the action name for logging (e.g., "Cancelled" or "Deleted").
   */
  protected abstract String getLogAction();
}
