package org.opentripplanner.ext.carpooling.filter;

import java.time.Duration;
import org.opentripplanner.ext.carpooling.model.CarpoolTrip;

/**
 * Interface for filtering carpool trips before expensive routing calculations.
 * <p>
 * Filters are applied as a pre-screening mechanism to quickly eliminate incompatible trips based on
 * various criteria (direction, capacity, time, distance, etc.).
 * Filters are applied as a pre-screening mechanism to quickly eliminate
 * incompatible trips based on various criteria (direction, capacity, time, distance, etc.).
 * <p>
 * Supports both direct routing (pickup + dropoff) and access/egress routing
 * (single passenger coordinate near a transit stop).
 */
public interface TripFilter {
  /**
   * Checks if a trip passes this filter for the given passenger request.
   *
   * @param trip         The carpool trip to evaluate
   * @param request      Passenger's journey preferences
   * @param searchWindow Time window around the requested departure time
   * @return true if the trip passes the filter, false otherwise
   */
  boolean accepts(CarpoolTrip trip, CarpoolingRequest request, Duration searchWindow);

  /**
   * Checks if a trip passes this filter for access/egress routing.
   * <p>
   * Used when evaluating carpool trip viability for connecting passengers
   * to public transit stops. Default implementation always returns true.
   *
   * @param trip Carpool trip
   * @param request      Passenger's journey preferences
   * @param searchWindow The time window around the requested departure time
   * @return true if the filter passes, false if it doesn't
   */
  default boolean acceptsAccessEgress(
    CarpoolTrip trip,
    CarpoolingRequest request,
    Duration searchWindow
  ) {
    return true;
  }
}
