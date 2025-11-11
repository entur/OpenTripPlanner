package org.opentripplanner.graph_builder.module.transfer.filter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.opentripplanner.routing.graphfinder.NearbyStop;

class CompositNearbyStopFilter implements NearbyStopFilter {

  private final List<NearbyStopFilter> filters;

  private CompositNearbyStopFilter(List<NearbyStopFilter> filters) {
    this.filters = filters;
  }

  static Builder of() {
    return new Builder();
  }

  @Override
  public Collection<NearbyStop> filterToStops(
    Collection<NearbyStop> nearbyStops,
    boolean reverseDirection
  ) {
    Set<NearbyStop> result = new HashSet<>();

    for (NearbyStopFilter it : filters) {
      result.addAll(it.filterToStops(nearbyStops, reverseDirection));
    }
    return result;
  }

  static class Builder {

    List<NearbyStopFilter> filters = new ArrayList<>();

    Builder add(NearbyStopFilter filter) {
      filters.add(filter);
      return this;
    }

    NearbyStopFilter build() {
      if (filters.size() == 1) {
        return filters.getFirst();
      }
      return new CompositNearbyStopFilter(filters);
    }
  }
}
