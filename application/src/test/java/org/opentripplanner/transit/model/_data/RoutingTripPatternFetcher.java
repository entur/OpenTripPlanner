package org.opentripplanner.transit.model._data;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.opentripplanner.routing.algorithm.raptoradapter.transit.TripPatternForDate;
import org.opentripplanner.transit.model.network.RoutingTripPattern;
import org.opentripplanner.transit.service.TransitService;

public class RoutingTripPatternFetcher {

  private final TransitService transitService;
  private final LocalDate serviceDate;

  public RoutingTripPatternFetcher(TransitService transitService, LocalDate serviceDate) {
    this.transitService = transitService;
    this.serviceDate = serviceDate;
  }

  public List<RoutingTripPattern> findRoutingTripPatterns() {
    return tripPatternsForDate(serviceDate)
      .stream()
      .map(TripPatternForDate::getTripPattern)
      .sorted()
      .toList();
  }

  public List<String> ids() {
    return findRoutingTripPatterns()
      .stream()
      .map(t -> t.getPattern().getId().toString())
      .sorted()
      .toList();
  }

  private Collection<TripPatternForDate> tripPatternsForDate(LocalDate serviceDate) {
    return transitService.getRealtimeRaptorTransitData().getTripPatternsForRunningDate(serviceDate);
  }
}
