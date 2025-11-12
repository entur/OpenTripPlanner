package org.opentripplanner.graph_builder.module.transfer.filter;

import java.util.Collection;
import org.opentripplanner.ext.flex.trip.FlexTrip;
import org.opentripplanner.routing.graphfinder.NearbyStop;
import org.opentripplanner.transit.model.framework.FeedScopedId;
import org.opentripplanner.transit.service.TransitService;

class FlexTripNearbyStopFilter implements NearbyStopFilter {

  private final TransitService transitService;

  FlexTripNearbyStopFilter(TransitService transitService) {
    this.transitService = transitService;
  }

  @Override
  public boolean includeFromStop(FeedScopedId id, boolean reverseDirection) {
    var stop = transitService.getStopLocation(id);
    return !transitService.getFlexIndex().getFlexTripsByStop(stop).isEmpty();
  }

  @Override
  public Collection<NearbyStop> filterToStops(
    Collection<NearbyStop> nearbyStops,
    boolean reverseDirection
  ) {
    MinMap<FlexTrip<?, ?>, NearbyStop> closestStopForFlexTrip = new MinMap<>();
    for (var it : nearbyStops) {
      var stop = it.stop;
      var flexTrips = transitService.getFlexIndex().getFlexTripsByStop(stop);

      for (FlexTrip<?, ?> trip : flexTrips) {
        if (reverseDirection ? trip.isAlightingPossible(stop) : trip.isBoardingPossible(stop)) {
          closestStopForFlexTrip.putMin(trip, it);
        }
      }
    }
    return closestStopForFlexTrip.values();
  }
}
