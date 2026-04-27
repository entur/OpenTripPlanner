package org.opentripplanner.ext.carpooling.filter;

import java.time.Duration;
import org.opentripplanner.ext.carpooling.model.CarpoolTrip;

/**
 * Interface for filtering carpool trips before expensive routing calculations.
 * <p>
 * Filters are applied as a pre-screening mechanism to quickly eliminate
 * incompatible trips based on various criteria (capacity, time, distance, etc.).
 * <p>
 * Supports both direct routing (pickup + dropoff) and access/egress routing
 * (single passenger coordinate near a transit stop).
 */
public interface TripFilter {
  /**
   * Checks if a trip passes this filter for the given passenger request.
   * <p>
   * Implementations are intentionally loose (necessary conditions only), because passengers board
   * and alight mid-route — using trip endpoints as tight bounds causes false negatives. Tight
   * enforcement is delegated to post-filters on the complete itinerary.
   *
   * @param trip         The carpool trip to evaluate
   * @param request      The passenger's journey preferences
   * @param searchWindow For depart-after: a trip is a candidate if it is still running at T
   *                     ({@code trip.endTime >= T}) and starts within the window
   *                     ({@code trip.startTime <= T + searchWindow}). A trip underway at T can
   *                     still pick up the passenger mid-route; tight enforcement is done by
   *                     {@link org.opentripplanner.ext.carpooling.filter.DepartAfterFilter}.
   *                     For arrive-by: a trip is a candidate if its driver has started at or
   *                     before T ({@code trip.startTime <= T}); tight enforcement is done by
   *                     {@link org.opentripplanner.ext.carpooling.filter.ArriveByFilter}.
   * @return true if the trip passes the filter, false otherwise
   */
  boolean accepts(CarpoolTrip trip, CarpoolingRequest request, Duration searchWindow);

  /**
   * Checks if a trip passes this filter for access/egress routing.
   * <p>
   * Used when evaluating carpool trip viability for connecting passengers to public transit stops.
   * Applies the same loose necessary-condition semantics as {@link #accepts}. Default
   * implementation always returns true.
   *
   * @param trip         The carpool trip to evaluate
   * @param request      The passenger's journey preferences
   * @param searchWindow Same semantics as in {@link #accepts}.
   * @return true if the trip passes the filter, false otherwise
   */
  default boolean acceptsAccessEgress(
    CarpoolTrip trip,
    CarpoolingRequest request,
    Duration searchWindow
  ) {
    return true;
  }
}
