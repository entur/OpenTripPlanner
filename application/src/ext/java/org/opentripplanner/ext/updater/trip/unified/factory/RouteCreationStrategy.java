package org.opentripplanner.ext.updater.trip.unified.factory;

import org.opentripplanner.ext.updater.trip.unified.model.command.TripCreationInfo;
import org.opentripplanner.transit.model.network.Route;
import org.opentripplanner.transit.service.TransitEditorService;
import org.opentripplanner.updater.spi.UpdateException;

/**
 * Strategy for resolving or creating a route for a new trip.
 * SIRI and GTFS-RT have different algorithms for agency resolution, route creation,
 * and submode derivation.
 */
public interface RouteCreationStrategy {
  /**
   * The result of resolving or creating a route.
   * @param route the resolved or newly created route
   * @param isNewRoute true if the route was newly created, false if it already existed
   */
  record RouteResolution(Route route, boolean isNewRoute) {}

  RouteResolution resolveOrCreateRoute(
    TripCreationInfo tripCreationInfo,
    TransitEditorService transitService
  ) throws UpdateException;
}
