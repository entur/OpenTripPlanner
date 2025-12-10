package org.opentripplanner.transit.model._data;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.opentripplanner.routing.algorithm.raptoradapter.transit.TripPatternForDate;
import org.opentripplanner.transit.model.network.RoutingTripPattern;
import org.opentripplanner.transit.service.TransitService;

/**
 * Convenience fetcher for routing trip patterns.
 */
public class RoutingTripPatternFetcher {

  private final TransitService transitService;
  private final LocalDate serviceDate;

  public RoutingTripPatternFetcher(TransitService transitService, LocalDate serviceDate) {
    this.transitService = transitService;
    this.serviceDate = serviceDate;
  }

  /**
   * Get the patterns for the given service data, extract their ids, convert to string and sort
   * them alphabetically.
   */
  public List<String> ids() {
    return list().stream().map(t -> t.getPattern().getId().toString()).sorted().toList();
  }

  public List<RoutingTripPattern> list() {
    final Collection<TripPatternForDate> tripPatternsForRunningDate = transitService
      .getRealtimeRaptorTransitData()
      .getTripPatternsForRunningDate(serviceDate);
    return tripPatternsForRunningDate.stream().map(TripPatternForDate::getTripPattern).toList();
  }
}
