package org.opentripplanner.updater.trip.handlers;

import org.opentripplanner.transit.model.framework.Result;
import org.opentripplanner.transit.model.network.TripPattern;
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
 * Handles trip cancellation updates.
 * Maps to GTFS-RT CANCELED and SIRI-ET cancellation=true.
 */
public class CancelTripHandler implements TripUpdateHandler {

  private static final Logger LOG = LoggerFactory.getLogger(CancelTripHandler.class);

  @Override
  public Result<RealTimeTripUpdate, UpdateError> handle(
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

    // Create the cancelled trip times
    var builder = tripTimes.createRealTimeFromScheduledTimes();
    builder.cancelTrip();

    // Create the RealTimeTripUpdate
    var realTimeTripUpdate = new RealTimeTripUpdate(pattern, builder.build(), serviceDate);

    LOG.debug("Cancelled trip {} on {}", trip.getId(), serviceDate);

    return Result.success(realTimeTripUpdate);
  }
}
