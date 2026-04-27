package org.opentripplanner.ext.carpooling.filter;

import java.time.Duration;
import java.util.List;
import org.opentripplanner.model.plan.Itinerary;

/**
 * Combines multiple {@link ItineraryFilter}s using AND logic, applied after routing to the fully
 * computed carpool itineraries. Use {@link #defaults()} for the standard configuration.
 */
public class PostFilters implements ItineraryFilter {

  private final List<ItineraryFilter> filters;

  public PostFilters(List<ItineraryFilter> filters) {
    this.filters = filters;
  }

  public static PostFilters defaults() {
    return new PostFilters(List.of(new ArriveByFilter(), new DepartAfterFilter()));
  }

  @Override
  public boolean accepts(Itinerary itinerary, CarpoolingRequest request, Duration searchWindow) {
    return filters.stream().allMatch(filter -> filter.accepts(itinerary, request, searchWindow));
  }
}
