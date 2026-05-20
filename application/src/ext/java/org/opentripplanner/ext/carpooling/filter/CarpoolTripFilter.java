package org.opentripplanner.ext.carpooling.filter;

import java.time.Duration;
import org.opentripplanner.ext.carpooling.model.CarpoolTrip;

/**
 * Pre-filter applied to carpool trip candidates before expensive routing calculations.
 * <p>
 * Filters are applied as a pre-screening mechanism to quickly eliminate incompatible trips using
 * estimated times and distances, limiting the computational cost of routing. Implementations are
 * intentionally loose (necessary conditions only), because passengers board and alight mid-route
 * — using trip endpoints as tight bounds causes false negatives. Tight enforcement of actual times
 * is delegated to post-filters on the complete itinerary.
 * <p>
 * Supports direct routing (pickup + dropoff) and access/egress routing (single passenger
 * coordinate near a transit stop). The routing mode is communicated via
 * {@link CarpoolingRequest#isAccessEgressRequest()} and {@link CarpoolingRequest#isAccessRequest()}.
 */
public interface CarpoolTripFilter {
  /**
   * Conservative cap on total passenger travel time, used as a fallback bound when an unbounded
   * transit segment separates the carpool leg from the request anchor.
   */
  Duration MAX_TOTAL_TRAVEL_TIME = Duration.ofHours(24);

  /**
   * Returns {@code true} if the trip is a viable candidate worth routing.
   * <p>
   * Uses estimated times as a necessary condition only to limit computational cost; tight
   * enforcement is done by post-filters after routing. The routing mode (direct, access, or egress)
   * is available via the {@code request}.
   *
   * @param trip         the carpool trip to evaluate
   * @param request      the passenger's journey preferences
   * @param searchWindow the routing search window; guaranteed non-null by callers
   * @return {@code true} if the trip is a candidate, {@code false} otherwise
   */
  boolean isCandidateTrip(CarpoolTrip trip, CarpoolingRequest request, Duration searchWindow);
}
