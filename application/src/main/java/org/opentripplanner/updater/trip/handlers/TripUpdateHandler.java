package org.opentripplanner.updater.trip.handlers;

import org.opentripplanner.transit.model.framework.Result;
import org.opentripplanner.transit.model.timetable.RealTimeTripUpdate;
import org.opentripplanner.transit.service.TransitEditorService;
import org.opentripplanner.updater.spi.UpdateError;
import org.opentripplanner.updater.trip.TripUpdateApplierContext;
import org.opentripplanner.updater.trip.model.ParsedTripUpdate;

/**
 * Interface for handling a specific type of trip update.
 * Each implementation handles one {@link org.opentripplanner.updater.trip.model.TripUpdateType}.
 */
public interface TripUpdateHandler {
  /**
   * Handle a parsed trip update and produce a real-time trip update.
   *
   * @param parsedUpdate the parsed update to handle
   * @param context the applier context
   * @param transitService the transit editor service
   * @return Result containing RealTimeTripUpdate on success, or UpdateError on failure
   */
  Result<RealTimeTripUpdate, UpdateError> handle(
    ParsedTripUpdate parsedUpdate,
    TripUpdateApplierContext context,
    TransitEditorService transitService
  );
}
