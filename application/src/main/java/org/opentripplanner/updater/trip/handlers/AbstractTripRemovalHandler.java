package org.opentripplanner.updater.trip.handlers;

import javax.annotation.Nullable;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.transit.model.framework.Result;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.timetable.RealTimeState;
import org.opentripplanner.transit.model.timetable.RealTimeTripTimesBuilder;
import org.opentripplanner.transit.model.timetable.RealTimeTripUpdate;
import org.opentripplanner.transit.model.timetable.Trip;
import org.opentripplanner.transit.model.timetable.TripTimes;
import org.opentripplanner.updater.spi.UpdateError;
import org.opentripplanner.updater.trip.TimetableSnapshotManager;
import org.opentripplanner.updater.trip.model.ResolvedTripRemoval;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract base class for handlers that remove trips (cancel or delete).
 * <p>
 * This handler first tries to cancel/delete a previously added real-time trip,
 * then falls back to cancelling/deleting a scheduled trip.
 */
public abstract class AbstractTripRemovalHandler implements TripUpdateHandler.ForTripRemoval {

  private static final Logger LOG = LoggerFactory.getLogger(AbstractTripRemovalHandler.class);

  @Nullable
  private final TimetableSnapshotManager snapshotManager;

  protected AbstractTripRemovalHandler(@Nullable TimetableSnapshotManager snapshotManager) {
    this.snapshotManager = snapshotManager;
  }

  @Override
  public final Result<TripUpdateResult, UpdateError> handle(ResolvedTripRemoval resolvedUpdate) {
    var serviceDate = resolvedUpdate.serviceDate();
    var tripId = resolvedUpdate.tripId();

    // First, try to cancel/delete a previously added trip
    if (snapshotManager != null && tripId != null) {
      var addedTripResult = cancelPreviouslyAddedTrip(
        tripId,
        serviceDate,
        snapshotManager,
        resolvedUpdate.dataSource()
      );
      if (addedTripResult != null) {
        return addedTripResult;
      }
    }

    // Not a previously added trip - try scheduled trip from resolved data
    Trip trip = resolvedUpdate.scheduledTrip();
    TripPattern pattern = resolvedUpdate.scheduledPattern();
    TripTimes tripTimes = resolvedUpdate.scheduledTripTimes();

    if (trip == null || pattern == null || tripTimes == null) {
      LOG.debug("No trip found for cancellation: {}", tripId);
      return Result.failure(
        new UpdateError(tripId, UpdateError.UpdateErrorType.NO_TRIP_FOR_CANCELLATION_FOUND)
      );
    }

    // Revert any previous real-time modifications to this scheduled trip
    if (snapshotManager != null) {
      snapshotManager.revertTripToScheduledTripPattern(trip.getId(), serviceDate);
    }

    // Create the modified trip times and apply removal
    var builder = tripTimes.createRealTimeFromScheduledTimes();
    applyRemoval(builder);

    var realTimeTripUpdate = new RealTimeTripUpdate(
      pattern,
      builder.build(),
      serviceDate,
      null,
      false,
      false,
      resolvedUpdate.dataSource()
    );

    LOG.debug("{} trip {} on {}", getLogAction(), trip.getId(), serviceDate);

    return Result.success(new TripUpdateResult(realTimeTripUpdate));
  }

  /**
   * Try to cancel/delete a previously added (real-time) trip.
   * Returns null if no added trip is found, otherwise returns the result.
   */
  private Result<TripUpdateResult, UpdateError> cancelPreviouslyAddedTrip(
    FeedScopedId tripId,
    java.time.LocalDate serviceDate,
    TimetableSnapshotManager snapshotManager,
    @Nullable String dataSource
  ) {
    // Check if there's a real-time pattern for this trip
    TripPattern pattern = snapshotManager.getNewTripPatternForModifiedTrip(tripId, serviceDate);
    if (pattern == null) {
      return null;
    }

    // Get trip times from the real-time timetable
    var timetable = snapshotManager.resolve(pattern, serviceDate);
    var tripTimes = timetable.getTripTimes(tripId);
    if (tripTimes == null) {
      LOG.debug(
        "Could not find trip times for previously added trip {} on {}",
        tripId,
        serviceDate
      );
      return null;
    }

    // Check if this trip was added via real-time (not just modified)
    if (tripTimes.getRealTimeState() != RealTimeState.ADDED) {
      return null;
    }

    // Cancel/delete the added trip
    var builder = tripTimes.createRealTimeFromScheduledTimes();
    applyRemoval(builder);

    var realTimeTripUpdate = new RealTimeTripUpdate(
      pattern,
      builder.build(),
      serviceDate,
      null,
      false,
      false,
      dataSource
    );

    LOG.debug("{} previously added trip {} on {}", getLogAction(), tripId, serviceDate);

    return Result.success(new TripUpdateResult(realTimeTripUpdate));
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
