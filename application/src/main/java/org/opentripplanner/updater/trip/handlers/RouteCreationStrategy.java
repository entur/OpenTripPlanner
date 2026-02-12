package org.opentripplanner.updater.trip.handlers;

import org.opentripplanner.transit.model.framework.Result;
import org.opentripplanner.transit.model.network.Route;
import org.opentripplanner.transit.service.TransitEditorService;
import org.opentripplanner.updater.spi.UpdateError;
import org.opentripplanner.updater.trip.model.TripCreationInfo;

/**
 * Strategy for resolving or creating a route for a new trip.
 * SIRI and GTFS-RT have different algorithms for agency resolution, route creation,
 * and submode derivation.
 */
public interface RouteCreationStrategy {
  Result<Route, UpdateError> resolveOrCreateRoute(
    TripCreationInfo tripCreationInfo,
    TransitEditorService transitService
  );
}
