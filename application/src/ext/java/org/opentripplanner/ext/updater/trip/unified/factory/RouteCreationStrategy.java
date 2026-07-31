package org.opentripplanner.ext.updater.trip.unified.factory;

import javax.annotation.Nullable;
import org.opentripplanner.ext.updater.trip.unified.model.command.TripCreationInfo;
import org.opentripplanner.transit.model.network.Route;
import org.opentripplanner.transit.model.organization.Operator;
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
   * @param netexSubmode the submode the created trip runs under, derived from the line it is
   *                     classified against - and stamped on the route as well when one is created.
   *                     Null when nothing classifies it, in which case the trip inherits the submode
   *                     of its route. No feed states a submode for a trip it adds, so it is derived
   *                     here rather than parsed.
   */
  record RouteResolution(Route route, boolean isNewRoute, @Nullable String netexSubmode) {}

  /**
   * @param operator the operator the created trip is operated by, already resolved against the
   *                 transit model - the created trip and the route it runs on are stamped with the
   *                 same operator, so it is resolved once, by the caller. Null when the message
   *                 names no operator or names one the transit model does not know.
   */
  RouteResolution resolveOrCreateRoute(
    TripCreationInfo tripCreationInfo,
    @Nullable Operator operator,
    TransitEditorService transitService
  ) throws UpdateException;
}
