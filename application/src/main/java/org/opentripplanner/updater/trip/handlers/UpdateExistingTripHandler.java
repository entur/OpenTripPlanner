package org.opentripplanner.updater.trip.handlers;

import org.opentripplanner.transit.model.framework.Result;
import org.opentripplanner.transit.model.timetable.RealTimeTripUpdate;
import org.opentripplanner.transit.service.TransitEditorService;
import org.opentripplanner.updater.spi.UpdateError;
import org.opentripplanner.updater.trip.TripUpdateApplierContext;
import org.opentripplanner.updater.trip.model.ParsedTripUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles updates to existing trips (delay updates, time changes).
 * Maps to GTFS-RT SCHEDULED and SIRI-ET regular updates.
 */
public class UpdateExistingTripHandler implements TripUpdateHandler {

  private static final Logger LOG = LoggerFactory.getLogger(UpdateExistingTripHandler.class);

  @Override
  public Result<RealTimeTripUpdate, UpdateError> handle(
    ParsedTripUpdate parsedUpdate,
    TripUpdateApplierContext context,
    TransitEditorService transitService
  ) {
    // Resolve the trip using the TripIdResolver
    var tripResult = context.tripIdResolver().resolveTrip(parsedUpdate.tripReference());
    if (tripResult.isFailure()) {
      LOG.debug("Could not resolve trip for update: {}", parsedUpdate.tripReference());
      return Result.failure(tripResult.failureValue());
    }

    var trip = tripResult.successValue();
    LOG.debug("Resolved trip {} for update", trip.getId());

    // TODO: Apply the actual time updates to the trip
    LOG.debug("UpdateExistingTripHandler apply logic not yet implemented");
    return Result.failure(UpdateError.noTripId(UpdateError.UpdateErrorType.UNKNOWN));
  }
}
