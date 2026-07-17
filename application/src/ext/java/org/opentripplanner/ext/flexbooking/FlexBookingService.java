package org.opentripplanner.ext.flexbooking;

import java.util.List;
import org.opentripplanner.ext.flex.FlexParameters;
import org.opentripplanner.model.plan.Itinerary;
import org.opentripplanner.routing.algorithm.raptoradapter.router.AdditionalSearchDays;
import org.opentripplanner.routing.api.request.RouteRequest;

/**
 * Routes direct flex itineraries against the real-time booked tours in the
 * {@link FlexBookingRepository}: a new passenger's pickup and dropoff are inserted into the
 * active vehicle's chain of booked stops when the booked stops' deviation budgets allow it.
 * <p>
 * Trips without a stored tour are not handled here — the static flex pipeline serves them. Trips
 * WITH a stored tour must be served exclusively by this service: the caller suppresses their
 * static results (see {@link #isRealTimeManaged}), so an infeasible insertion yields no itinerary
 * rather than one with times the vehicle cannot honor.
 */
public interface FlexBookingService {
  /**
   * Direct flex itineraries produced by inserting the passenger into stored booked tours.
   * Returns an empty list when the request's direct mode is not FLEXIBLE or no feasible
   * insertion exists.
   */
  List<Itinerary> routeDirect(
    RouteRequest request,
    FlexParameters flexParameters,
    AdditionalSearchDays additionalSearchDays
  );

  /**
   * Whether the itinerary contains a flex leg governed by a stored real-time tour — i.e. a
   * static direct-flex result that must be suppressed in favor of this service's results.
   */
  boolean containsRealTimeManagedLeg(Itinerary itinerary);
}
