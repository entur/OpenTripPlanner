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
 * Handles adding new trips that are not in the schedule.
 * Maps to GTFS-RT NEW/ADDED and SIRI-ET extra journeys.
 */
public class AddNewTripHandler implements TripUpdateHandler {

  private static final Logger LOG = LoggerFactory.getLogger(AddNewTripHandler.class);

  @Override
  public Result<RealTimeTripUpdate, UpdateError> handle(
    ParsedTripUpdate parsedUpdate,
    TripUpdateApplierContext context,
    TransitEditorService transitService
  ) {
    // TODO: Implement
    LOG.debug("AddNewTripHandler not yet implemented");
    return Result.failure(UpdateError.noTripId(UpdateError.UpdateErrorType.UNKNOWN));
  }
}
