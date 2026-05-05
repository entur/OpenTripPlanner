package org.opentripplanner.routing.algorithm.raptoradapter.router.onboardaccess;

import java.util.stream.IntStream;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.routing.algorithm.raptoradapter.transit.mappers.LookupStopIndexCallback;
import org.opentripplanner.transit.model.framework.EntityNotFoundException;
import org.opentripplanner.transit.model.site.StopLocation;
import org.opentripplanner.transit.service.TransitService;

/**
 * Resolves a stop's index or a station's list of child-stop indices from the TransitService.
 */
public class TransitServiceStopIndexResolver implements LookupStopIndexCallback {

  private final TransitService transitService;

  public TransitServiceStopIndexResolver(TransitService transitService) {
    this.transitService = transitService;
  }

  @Override
  public IntStream lookupStopLocationIndexes(FeedScopedId stopLocationId) {
    var stop = transitService.getRegularStop(stopLocationId);
    if (stop != null) {
      return IntStream.of(stop.getIndex());
    }

    var station = transitService.getStation(stopLocationId);
    if (station != null) {
      return station.getChildStops().stream().mapToInt(StopLocation::getIndex);
    }

    throw new EntityNotFoundException(
      "Stop, station, multimodal station or group of stations",
      stopLocationId
    );
  }
}
