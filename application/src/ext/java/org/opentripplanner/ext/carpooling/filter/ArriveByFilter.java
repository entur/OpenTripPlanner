package org.opentripplanner.ext.carpooling.filter;

import java.time.Duration;
import org.opentripplanner.model.plan.Itinerary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Post-filter that rejects carpool itineraries arriving after the passenger's requested arrival
 * time.
 * <p>
 * Accepts any itinerary whose end time is at or before T ((-∞, T]). The search window is not
 * used here — early-arrival filtering is a separate concern deferred to a future implementation.
 * <p>
 * Only active for arrive-by requests; depart-after itineraries are always accepted.
 */
public class ArriveByFilter implements ItineraryFilter {

  private static final Logger LOG = LoggerFactory.getLogger(ArriveByFilter.class);

  @Override
  public boolean accepts(Itinerary itinerary, CarpoolingRequest request, Duration searchWindow) {
    if (!request.isArriveByRequest() || request.getRequestedDateTime() == null) {
      return true;
    }

    var requestedArriveByTime = request.getRequestedDateTime();
    var arrivalTime = itinerary.endTimeAsInstant();
    var arrivesOnTime = !arrivalTime.isAfter(requestedArriveByTime);

    if (!arrivesOnTime) {
      LOG.debug(
        "Trip {} rejected by arrive-by post-filter: arrives at {}, deadline is {}",
        itinerary.keyAsString(),
        arrivalTime,
        requestedArriveByTime
      );
    }

    return arrivesOnTime;
  }
}
