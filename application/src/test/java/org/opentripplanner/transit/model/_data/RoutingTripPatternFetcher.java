package org.opentripplanner.transit.model._data;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.opentripplanner.routing.algorithm.raptoradapter.transit.TripPatternForDate;
import org.opentripplanner.transit.model.network.RoutingTripPattern;
import org.opentripplanner.transit.model.timetable.TripTimes;
import org.opentripplanner.transit.service.TransitService;

/**
 * Convenience fetcher for routing trip patterns.
 */
public class RoutingTripPatternFetcher {

  private final TransitService transitService;
  private final LocalDate serviceDate;
  private final CancellationFilter filter;

  enum CancellationFilter {
    INCLUDE_CANCELLED,
    EXCLUDE_CANCELLED,
  }

  RoutingTripPatternFetcher(
    TransitService transitService,
    LocalDate serviceDate,
    CancellationFilter filter
  ) {
    this.transitService = transitService;
    this.serviceDate = serviceDate;
    this.filter = filter;
  }

  /**
   * Returns a copy of this fetcher that excludes cancelled trips.
   */
  public RoutingTripPatternFetcher excludeCancelled() {
    return new RoutingTripPatternFetcher(
      transitService,
      serviceDate,
      CancellationFilter.EXCLUDE_CANCELLED
    );
  }

  /**
   * Get the patterns for the given service data, extract their ids, converts to string and sort
   * them alphabetically.
   */
  public List<String> ids() {
    return list().stream().map(t -> t.getPattern().getId().toString()).sorted().toList();
  }

  public List<RoutingTripPattern> list() {
    Collection<TripPatternForDate> tripPatternsForRunningDate = transitService
      .getRealtimeRaptorTransitData()
      .getTripPatternsForRunningDate(serviceDate);

    if (filter == CancellationFilter.EXCLUDE_CANCELLED) {
      tripPatternsForRunningDate = tripPatternsForRunningDate
        .stream()
        .filter(t -> !t.tripTimes().stream().allMatch(TripTimes::isCanceledOrDeleted))
        .toList();
    }
    return tripPatternsForRunningDate.stream().map(TripPatternForDate::getTripPattern).toList();
  }
}
