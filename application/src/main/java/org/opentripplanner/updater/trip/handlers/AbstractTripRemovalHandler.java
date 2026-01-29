package org.opentripplanner.updater.trip.handlers;

import org.opentripplanner.transit.model.framework.Result;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.timetable.RealTimeTripTimesBuilder;
import org.opentripplanner.transit.model.timetable.RealTimeTripUpdate;
import org.opentripplanner.transit.model.timetable.Trip;
import org.opentripplanner.transit.model.timetable.TripTimes;
import org.opentripplanner.transit.service.TransitEditorService;
import org.opentripplanner.updater.spi.UpdateError;
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
  public final Result<RealTimeTripUpdate, UpdateError> handle(
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

    // Resolve the trip from the trip reference
    var tripResult = tripResolver.resolveTrip(tripReference);
    if (tripResult.isFailure()) {
      return Result.failure(tripResult.failureValue());
    }

    Trip trip = tripResult.successValue();

    // Find the scheduled pattern for this trip (not the real-time modified pattern)
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
    var snapshotManager = context.snapshotManager();
    if (snapshotManager != null) {
      snapshotManager.revertTripToScheduledTripPattern(trip.getId(), serviceDate);
    }

    // Create the modified trip times
    var builder = tripTimes.createRealTimeFromScheduledTimes();
    applyRemoval(builder);

    // Create the RealTimeTripUpdate
    var realTimeTripUpdate = new RealTimeTripUpdate(pattern, builder.build(), serviceDate);

    LOG.debug("{} trip {} on {}", getLogAction(), trip.getId(), serviceDate);

    return Result.success(realTimeTripUpdate);
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
