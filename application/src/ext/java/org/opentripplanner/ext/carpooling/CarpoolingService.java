package org.opentripplanner.ext.carpooling;

import java.util.List;
import org.opentripplanner.model.plan.Itinerary;
import org.opentripplanner.routing.api.request.RouteRequest;
import org.opentripplanner.routing.error.RoutingValidationException;

/**
 * Service for finding carpooling options by matching passenger requests with available driver trips.
 * <p>
 * Carpooling enables passengers to join existing driver journeys by being picked up and dropped off
 * along the driver's route. The service finds optimal insertion points for new passengers while
 * respecting capacity constraints, time windows, and route deviation budgets.
 */
public interface CarpoolingService {
  /**
   * Finds carpooling itineraries matching the passenger's routing request.
   * <p>
   *
   * @param request the routing request containing passenger origin, destination, and preferences
   * @return list of carpool itineraries, sorted by quality (additional travel time), may be empty
   *         if no compatible trips found. Results are limited to avoid overwhelming users.
   * @throws RoutingValidationException if the request is invalid (missing origin/destination,
   *         invalid coordinates, etc.)
   * @throws IllegalArgumentException if request is null
   */
  List<Itinerary> route(RouteRequest request) throws RoutingValidationException;
}
