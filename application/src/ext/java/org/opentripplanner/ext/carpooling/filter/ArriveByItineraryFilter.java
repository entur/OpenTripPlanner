package org.opentripplanner.ext.carpooling.filter;

import java.time.Duration;
import org.opentripplanner.model.plan.Itinerary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Post-filter that rejects carpool itineraries whose actual arrival time falls outside the
 * passenger's requested arrive-by window.
 * <p>
 * Only active for arrive-by requests; depart-after itineraries are always accepted.
 * <ul>
 *   <li>Upper bound: {@code itinerary.endTime <= T} — the itinerary must not arrive after the
 *       requested time.</li>
 *   <li>Lower bound: {@code itinerary.endTime >= T - searchWindow} — the itinerary must not
 *       arrive too far before the requested time.</li>
 * </ul>
 */
public class ArriveByItineraryFilter implements CarpoolItineraryFilter {

  private static final Logger LOG = LoggerFactory.getLogger(ArriveByItineraryFilter.class);

  @Override
  public boolean isValidItinerary(
    Itinerary itinerary,
    CarpoolingRequest request,
    Duration searchWindow
  ) {
    if (!request.isArriveByRequest() || request.getRequestedDateTime() == null) {
      return true;
    }

    var requestedArrivalTime = request.getRequestedDateTime();
    var endTime = itinerary.endTimeAsInstant();

    if (endTime.isAfter(requestedArrivalTime)) {
      LOG.debug(
        "Itinerary {} rejected by arrive-by post-filter: arrives at {}, requested arrive-by {} — {} too late",
        itinerary.keyAsString(),
        endTime,
        requestedArrivalTime,
        Duration.between(requestedArrivalTime, endTime)
      );
      return false;
    }

    var lowerBound = requestedArrivalTime.minus(searchWindow);
    if (endTime.isBefore(lowerBound)) {
      LOG.debug(
        "Itinerary {} rejected by arrive-by post-filter: arrives at {}, which is before lower bound {}",
        itinerary.keyAsString(),
        endTime,
        lowerBound
      );
      return false;
    }

    return true;
  }
}
