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
 * Handles adding extra calls (stops) to an existing trip.
 * This is a SIRI-ET specific feature (extra calls).
 */
public class AddExtraCallsHandler implements TripUpdateHandler {

  private static final Logger LOG = LoggerFactory.getLogger(AddExtraCallsHandler.class);

  @Override
  public Result<RealTimeTripUpdate, UpdateError> handle(
    ParsedTripUpdate parsedUpdate,
    TripUpdateApplierContext context,
    TransitEditorService transitService
  ) {
    // TODO: Implement
    LOG.debug("AddExtraCallsHandler not yet implemented");
    return Result.failure(UpdateError.noTripId(UpdateError.UpdateErrorType.UNKNOWN));
  }
}
