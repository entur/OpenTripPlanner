package org.opentripplanner.ext.carpooling.filter;

import java.time.Duration;
import org.opentripplanner.model.plan.Itinerary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Post-filter that rejects carpool itineraries whose actual departure time falls outside the
 * passenger's requested depart-after window.
 * <p>
 * Only active for depart-after requests; arrive-by itineraries are always accepted.
 * <ul>
 *   <li>Lower bound: {@code itinerary.startTime >= T} — the itinerary must not depart before
 *       the requested time.</li>
 *   <li>Upper bound: {@code itinerary.startTime <= T + searchWindow + maxWalkTime} — the
 *       itinerary must not depart too far into the future. A 15-minute walk buffer accounts for
 *       the passenger walking to the pickup point. For egress legs the upper bound is relaxed
 *       by an additional 24 hours because the actual transit arrival time is unknown.</li>
 * </ul>
 * The upper bound is only enforced when {@code searchWindow} is non-null.
 */
public class DepartAfterItineraryFilter implements CarpoolItineraryFilter {

  private static final Logger LOG = LoggerFactory.getLogger(DepartAfterItineraryFilter.class);

  @Override
  public boolean isValidItinerary(
    Itinerary itinerary,
    CarpoolingRequest request,
    Duration searchWindow
  ) {
    if (request.isArriveByRequest() || request.getRequestedDateTime() == null) {
      return true;
    }

    var requestedDepartureTime = request.getRequestedDateTime();
    var startTime = itinerary.startTimeAsInstant();

    if (startTime.isBefore(requestedDepartureTime)) {
      LOG.debug(
        "Itinerary {} rejected by depart-after post-filter: departs at {}, requested depart-after {} — {} too early",
        itinerary.keyAsString(),
        startTime,
        requestedDepartureTime,
        Duration.between(startTime, requestedDepartureTime)
      );
      return false;
    }

    if (searchWindow != null) {
      var upperBound = requestedDepartureTime
        .plus(searchWindow)
        .plus(CarpoolTripFilter.MAX_WALK_TIME);
      if (request.isEgressRequest()) {
        upperBound = upperBound.plus(CarpoolTripFilter.EGRESS_SLACK);
      }
      if (startTime.isAfter(upperBound)) {
        LOG.debug(
          "Itinerary {} rejected by depart-after post-filter: departs at {}, which is after upper bound {}",
          itinerary.keyAsString(),
          startTime,
          upperBound
        );
        return false;
      }
    }

    return true;
  }
}
