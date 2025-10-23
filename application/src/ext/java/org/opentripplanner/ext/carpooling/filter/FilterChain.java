package org.opentripplanner.ext.carpooling.filter;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.opentripplanner.ext.carpooling.model.CarpoolTrip;
import org.opentripplanner.framework.geometry.WgsCoordinate;

/**
 * Combines multiple trip filters using AND logic (all filters must pass).
 * <p>
 * Filters are evaluated in order, with short-circuit evaluation:
 * as soon as one filter rejects a trip, evaluation stops.
 * <p>
 * The standard filter chain includes (in order of performance impact):
 * 1. CapacityFilter - Very fast (O(1))
 * 2. TimeBasedFilter - Very fast (O(1))
 * 3. DistanceBasedFilter - Fast (O(1) with 4 distance calculations)
 * 4. DirectionalCompatibilityFilter - Medium (O(n) with n = number of stops)
 */
public class FilterChain implements TripFilter {

  private final List<TripFilter> filters;

  public FilterChain(List<TripFilter> filters) {
    this.filters = filters;
  }

  /**
   * Creates a standard filter chain with all recommended filters.
   * <p>
   * Filters are ordered by performance impact (fastest first) to maximize
   * the benefit of short-circuit evaluation.
   */
  public static FilterChain standard() {
    return new FilterChain(
      List.of(
        new CapacityFilter(), // Fastest: O(1)
        new TimeBasedFilter(), // Very fast: O(1)
        new DistanceBasedFilter(), // Fast: O(1) with 4 distance calculations
        new DirectionalCompatibilityFilter() // Medium: O(n) segments
      )
    );
  }

  @Override
  public boolean accepts(
    CarpoolTrip trip,
    WgsCoordinate passengerPickup,
    WgsCoordinate passengerDropoff
  ) {
    for (TripFilter filter : filters) {
      if (!filter.accepts(trip, passengerPickup, passengerDropoff)) {
        return false; // Short-circuit: filter rejected the trip
      }
    }
    return true; // All filters passed
  }

  @Override
  public boolean accepts(
    CarpoolTrip trip,
    WgsCoordinate passengerPickup,
    WgsCoordinate passengerDropoff,
    Instant passengerDepartureTime,
    Duration searchWindow
  ) {
    for (TripFilter filter : filters) {
      if (
        !filter.accepts(
          trip,
          passengerPickup,
          passengerDropoff,
          passengerDepartureTime,
          searchWindow
        )
      ) {
        return false; // Short-circuit: filter rejected the trip
      }
    }
    return true; // All filters passed
  }
}
