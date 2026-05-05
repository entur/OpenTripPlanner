package org.opentripplanner.routing.algorithm.raptoradapter.router.onboardaccess;

import java.util.stream.IntStream;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.transit.model.framework.EntityNotFoundException;
import org.opentripplanner.transit.model.site.StopLocation;
import org.opentripplanner.transit.service.TransitService;

/**
 * Resolves a stop's index or a station's list of child-stop indices from the TransitService.
 */
public class StopIndicesResolver {

  private final TransitService transitService;

  public StopIndicesResolver(TransitService transitService) {
    this.transitService = transitService;
  }

  public IntStream resolve(FeedScopedId stopLocationId) {
    var stop = transitService.getRegularStop(stopLocationId);
    if (stop != null) {
      return IntStream.of(stop.getIndex());
    }

    var station = transitService.getStation(stopLocationId);
    if (station != null) {
      return station.getChildStops().stream().mapToInt(StopLocation::getIndex);
    }

    throw new EntityNotFoundException("Stop or station", stopLocationId);
  }
}
