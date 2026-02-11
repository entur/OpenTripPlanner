package org.opentripplanner.updater.trip.handlers;

import org.opentripplanner.transit.model.framework.Result;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.timetable.RealTimeTripTimesBuilder;
import org.opentripplanner.transit.model.timetable.RealTimeTripUpdate;
import org.opentripplanner.transit.model.timetable.Trip;
import org.opentripplanner.transit.model.timetable.TripTimes;
import org.opentripplanner.transit.service.TransitEditorService;
import org.opentripplanner.updater.spi.UpdateError;
import org.opentripplanner.updater.trip.TimetableSnapshotManager;
import org.opentripplanner.updater.trip.TripUpdateApplierContext;
import org.opentripplanner.updater.trip.model.ParsedTripUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract base class for handlers that remove trips (cancel or delete).
 * Provides common logic for trip resolution, pattern lookup, and snapshot management.
 */
public abstract class AbstractTripRemovalHandler implements TripUpdateHandler {

  private static final Logger LOG = LoggerFactory.getLogger(AbstractTripRemovalHandler.class);

  @Override
  public final Result<TripUpdateResult, UpdateError> handle(
    ParsedTripUpdate parsedUpdate,
    TripUpdateApplierContext context,
    TransitEditorService transitService
  ) {
    var tripReference = parsedUpdate.tripReference();
    var tripResolver = context.tripResolver();

    // Resolve service date (from parsedUpdate or from tripOnServiceDateId)
    var serviceDateResult = context.serviceDateResolver().resolveServiceDate(parsedUpdate);
    if (serviceDateResult.isFailure()) {
      return Result.failure(serviceDateResult.failureValue());
    }
    var serviceDate = serviceDateResult.successValue();

    // First, try to cancel a previously added (real-time) trip
    var snapshotManager = context.snapshotManager();
    if (snapshotManager != null) {
      var addedTripResult = cancelPreviouslyAddedTrip(
        tripReference.tripId(),
        serviceDate,
        snapshotManager
      );
      if (addedTripResult != null) {
        return addedTripResult;
      }
    }

    // Resolve the trip from the trip reference (for scheduled trips)
    var tripResult = tripResolver.resolveTrip(tripReference);
    if (tripResult.isFailure()) {
      return Result.failure(tripResult.failureValue());
    }

    Trip trip = tripResult.successValue();

    // Find the scheduled pattern for this trip
    TripPattern pattern = transitService.findPattern(trip);
    if (pattern == null) {
      LOG.warn("No pattern found for trip {}", trip.getId());
      return Result.failure(
        new UpdateError(trip.getId(), UpdateError.UpdateErrorType.NO_TRIP_FOR_CANCELLATION_FOUND)
      );
    }

    // Get the trip times from the scheduled timetable
    TripTimes tripTimes = pattern.getScheduledTimetable().getTripTimes(trip);
    if (tripTimes == null) {
      LOG.warn("No trip times found for trip {} in pattern {}", trip.getId(), pattern.getId());
      return Result.failure(
        new UpdateError(trip.getId(), UpdateError.UpdateErrorType.NO_TRIP_FOR_CANCELLATION_FOUND)
      );
    }

    // Revert any previous real-time modifications to this trip on this service date
    if (snapshotManager != null) {
      snapshotManager.revertTripToScheduledTripPattern(trip.getId(), serviceDate);
    }

    // Create the modified trip times
    var builder = tripTimes.createRealTimeFromScheduledTimes();
    applyRemoval(builder);

    // Create the RealTimeTripUpdate
    var realTimeTripUpdate = new RealTimeTripUpdate(pattern, builder.build(), serviceDate);

    LOG.debug("{} trip {} on {}", getLogAction(), trip.getId(), serviceDate);

    return Result.success(new TripUpdateResult(realTimeTripUpdate));
  }

  /**
   * Attempt to cancel a previously added (real-time) trip.
   * Returns a success result if the trip was found and cancelled, null otherwise.
   */
  private Result<TripUpdateResult, UpdateError> cancelPreviouslyAddedTrip(
    org.opentripplanner.core.model.id.FeedScopedId tripId,
    java.time.LocalDate serviceDate,
    TimetableSnapshotManager snapshotManager
  ) {
    // Check if there's a real-time pattern for this trip
    TripPattern pattern = snapshotManager.getNewTripPatternForModifiedTrip(tripId, serviceDate);

    if (pattern == null) {
      LOG.debug("No real-time pattern found for trip {} on {}", tripId, serviceDate);
      return null;
    }

    // Check if this is actually a previously added trip (not just a modified scheduled trip)
    var timetable = snapshotManager.resolve(pattern, serviceDate);
    var tripTimes = timetable.getTripTimes(tripId);

    if (tripTimes == null) {
      LOG.debug(
        "No trip times found in real-time timetable for trip {} in pattern {}",
        tripId,
        pattern.getId()
      );
      return null;
    }

    // Check if this trip was added via real-time (not in scheduled timetable)
    if (!isAddedTrip(tripTimes)) {
      LOG.debug("Trip {} is not an added trip, state is {}", tripId, tripTimes.getRealTimeState());
      return null;
    }

    // Cancel the added trip
    var builder = tripTimes.createRealTimeFromScheduledTimes();
    applyRemoval(builder);

    LOG.debug("{} previously added trip {} on {}", getLogAction(), tripId, serviceDate);

    return Result.success(
      new TripUpdateResult(new RealTimeTripUpdate(pattern, builder.build(), serviceDate))
    );
  }

  private boolean isAddedTrip(TripTimes tripTimes) {
    return (
      tripTimes.getRealTimeState() ==
      org.opentripplanner.transit.model.timetable.RealTimeState.ADDED
    );
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
