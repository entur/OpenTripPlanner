package org.opentripplanner.ext.carpooling.filter;

import java.time.Duration;
import org.opentripplanner.model.plan.Itinerary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Post-filter that rejects carpool itineraries departing before the passenger's requested
 * departure time.
 * <p>
 * Only active for depart-after requests; arrive-by itineraries are always accepted.
 * <p>
 * This filter enforces {@code itinerary.startTime >= T} — the lower bound of the depart-after
 * window. The upper bound ({@code itinerary.startTime <= T + searchWindow}) is not enforced here.
 * The pre-filter ({@link TimeBasedFilter}) enforced {@code trip.startTime <= T + searchWindow},
 * which is a necessary but not sufficient condition: for mid-route pickups the actual boarding
 * time can exceed {@code trip.startTime}. Enforcing the upper bound on itinerary time is deferred
 * to a future implementation.
 */
public class DepartAfterFilter implements ItineraryFilter {

  private static final Logger LOG = LoggerFactory.getLogger(DepartAfterFilter.class);

  @Override
  public boolean accepts(Itinerary itinerary, CarpoolingRequest request, Duration searchWindow) {
    if (request.isArriveByRequest() || request.getRequestedDateTime() == null) {
      return true;
    }

    var requestedDepartureTime = request.getRequestedDateTime();
    var departureTime = itinerary.startTimeAsInstant();
    var waitTime = Duration.between(requestedDepartureTime, departureTime);
    var isWithinWindow = !waitTime.isNegative();

    if (!isWithinWindow) {
      LOG.debug(
        "Itinerary {} rejected by depart-after post-filter: departs at {}, passenger requests depart-after {} — departed {} too early",
        itinerary.keyAsString(),
        departureTime,
        requestedDepartureTime,
        waitTime.negated()
      );
    }

    return isWithinWindow;
  }
}
