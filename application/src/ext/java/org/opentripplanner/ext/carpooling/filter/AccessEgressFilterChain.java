package org.opentripplanner.ext.carpooling.filter;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.opentripplanner.ext.carpooling.model.CarpoolTrip;
import org.opentripplanner.routing.algorithm.raptoradapter.router.street.AccessEgressType;
import org.opentripplanner.routing.graphfinder.NearbyStop;
import org.opentripplanner.street.geometry.WgsCoordinate;

public class AccessEgressFilterChain {

  private final List<AccessEgressTripFilter> filters;

  public AccessEgressFilterChain(List<AccessEgressTripFilter> filters) {
    this.filters = filters;
  }

  /**
   * Creates a standard filter chain with all recommended filters.
   * <p>
   * Filters are ordered by performance impact (fastest first) to maximize
   * the benefit of short-circuit evaluation.
   */
  public static AccessEgressFilterChain standard() {
    return new AccessEgressFilterChain(
      List.of(
        new CapacityFilter(),
        new TimeBasedFilter(),
        new DistanceBasedFilter(),
        new DirectionalCompatibilityFilter()
      )
    );
  }

  public boolean accepts(
    CarpoolTrip trip,
    WgsCoordinate coordinateOfPassenger, // Origin for access, destination for egress
    Instant passengerDepartureTime,
    Duration searchWindow
  ) {
    return filters
      .stream()
      .allMatch(filter ->
        filter.acceptsAccessEgress(
          trip,
          coordinateOfPassenger,
          passengerDepartureTime,
          searchWindow
        )
      );
  }
}
