package org.opentripplanner.ext.carpooling.filter;

import java.time.Duration;
import java.util.List;
import org.opentripplanner.model.plan.Itinerary;

public class Postfilters implements ItineraryFilter {

  private final List<ItineraryFilter> filters;

  public Postfilters(List<ItineraryFilter> filters) {
    this.filters = filters;
  }

  public static Postfilters defaults() {
    return new Postfilters(List.of(new ArriveByFilter()));
  }

  @Override
  public boolean accepts(Itinerary itinerary, CarpoolingRequest request, Duration searchWindow) {
    return filters.stream().allMatch(filter -> filter.accepts(itinerary, request, searchWindow));
  }
}
