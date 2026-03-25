package org.opentripplanner.ext.carpooling.filter;

import java.time.Duration;
import org.opentripplanner.model.plan.Itinerary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ArriveByFilter implements ItineraryFilter {

  private static final Logger LOG = LoggerFactory.getLogger(ArriveByFilter.class);

  @Override
  public boolean accepts(Itinerary itinerary, CarpoolingRequest request, Duration searchWindow) {
    if (!request.isArriveByRequest() || request.getRequestedDateTime() == null) {
      return true;
    }

    var requestedArriveByTime = request.getRequestedDateTime();
    var arrivalTime = itinerary.endTimeAsInstant();
    var difference = Duration.between(requestedArriveByTime, arrivalTime).abs();
    var isWithinWindow = difference.compareTo(searchWindow) <= 0;

    if (!isWithinWindow) {
      LOG.debug(
        "Trip {} rejected by arrive by post filter: trip arrival at {}, passenger requests {}, diff = {} (window = {})",
        itinerary.keyAsString(),
        arrivalTime,
        requestedArriveByTime,
        difference,
        searchWindow
      );
    }

    return isWithinWindow;
  }
}
