package org.opentripplanner.updater.trip.handlers;

import org.opentripplanner.transit.model.framework.Result;
import org.opentripplanner.transit.service.TransitEditorService;
import org.opentripplanner.updater.spi.UpdateError;
import org.opentripplanner.updater.trip.TripUpdateApplierContext;
import org.opentripplanner.updater.trip.model.ResolvedTripUpdate;

/**
 * Interface for handling a specific type of trip update.
 * Each implementation handles one {@link org.opentripplanner.updater.trip.model.TripUpdateType}.
 * <p>
 * Handlers receive a {@link ResolvedTripUpdate} which contains all resolved data (trip, pattern,
 * service date, trip times) so handlers can focus on their specific transformation logic without
 * duplicating resolution code.
 */
public interface TripUpdateHandler {
  /**
   * Handle a resolved trip update and produce a real-time trip update.
   *
   * @param resolvedUpdate the resolved update to handle (with trip, pattern, etc. already resolved)
   * @param context the applier context
   * @param transitService the transit editor service
   * @return Result containing TripUpdateResult on success, or UpdateError on failure
   */
  Result<TripUpdateResult, UpdateError> handle(
    ResolvedTripUpdate resolvedUpdate,
    TripUpdateApplierContext context,
    TransitEditorService transitService
  );
}
