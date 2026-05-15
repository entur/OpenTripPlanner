package org.opentripplanner.ext.carpooling.filter;

import java.time.Duration;
import java.time.Instant;
import org.opentripplanner.ext.carpooling.model.CarpoolTrip;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pre-filters carpool trip candidates for {@code arriveBy = false} (depart-after) requests.
 * Arrive-by requests pass through unconditionally; their pre-filtering is handled by
 * {@link ArriveByTripFilter}.
 * <p>
 * A trip is rejected only when it is provably outside the passenger's departure window, never on
 * a "this looks unlikely" basis. Trips that pass are still subject to expensive routing and a
 * tighter post-filter on the actual itinerary times.
 *
 * <h2>Variables</h2>
 *
 * <h3>Request window</h3>
 *
 * For {@code arriveBy = false}, the passenger's departure window is derived from
 * {@code request.dateTime()} and the {@code searchWindow}:
 *
 * <ul>
 *   <li><strong>EDT</strong> — earliest departure time = {@code dateTime}. The passenger does
 *       not depart from origin before EDT.</li>
 *   <li><strong>LDT</strong> — latest departure time = {@code dateTime + searchWindow}. The
 *       passenger departs by LDT.</li>
 * </ul>
 *
 * <h3>Slack constants</h3>
 *
 * The carpool window is {@code [trip.startTime, trip.endTime]} but the passenger boards and
 * alights somewhere in the middle. Two slacks pad the bounds outward:
 *
 * <ul>
 *   <li><strong>{@code W} = {@link CarpoolTripFilter#MAX_WALK_TIME}</strong> (max walk time) —
 *       upper bound on how long the passenger walks between origin/destination and the carpool
 *       pickup/dropoff. A passenger leaving by LDT can be at the pickup as late as
 *       {@code LDT + W}. <em>Concrete and known per request</em> — in principle this should come
 *       from {@code request.preferences().street().accessEgress().maxDuration().valueOf(WALK)};
 *       today the constant is hardcoded.</li>
 *   <li><strong>{@code T} = {@link CarpoolTripFilter#MAX_TOTAL_TRAVEL_TIME}</strong> (max total
 *       passenger travel time, fallback) — used <em>only</em> for the egress/too-late cell, where
 *       access + transit between the origin anchor and the egress carpool is otherwise unbounded.
 *       {@code T} caps that unknown duration with a deliberately conservative number so the
 *       filter degrades to "almost a no-op" rather than producing false negatives.</li>
 * </ul>
 *
 * <h2>Rules (the {@code arriveBy = false} half)</h2>
 *
 * <pre>
 * | Leg type | Reject as TOO EARLY    | Reject as TOO LATE     |
 * |----------|------------------------|------------------------|
 * | Direct   | tripEnd   &lt; EDT        | tripStart &gt; LDT + W    |
 * | Access   | tripEnd   &lt; EDT        | tripStart &gt; LDT + W    |
 * | Egress   | tripEnd   &lt; EDT        | tripStart &gt; LDT + T    |  (T fallback)
 * </pre>
 *
 * <h3>Why these shapes</h3>
 *
 * <ul>
 *   <li><em>Too early</em> compares {@code tripEnd} (latest moment any pickup along the route can
 *       happen) against EDT. No slack is subtracted because walks before pickup only delay the
 *       passenger's arrival at the pickup, never advance it.</li>
 *   <li><em>Too late</em> compares {@code tripStart} (earliest moment any pickup along the route
 *       can happen) against {@code LDT + slack}. {@code W} applies for direct & access because a
 *       single pickup walk shifts the passenger's latest pickup-arrival by W; {@code T} replaces
 *       {@code W} for egress because access + transit between the origin anchor and the egress
 *       carpool is otherwise unbounded.</li>
 * </ul>
 *
 * <h3>Behavior with missing inputs</h3>
 *
 * <ul>
 *   <li>{@code dateTime == null}: pass-through (no filtering possible).</li>
 * </ul>
 */
public class DepartAfterTripFilter implements CarpoolTripFilter {

  private static final Logger LOG = LoggerFactory.getLogger(DepartAfterTripFilter.class);

  @Override
  public boolean isCandidateTrip(
    CarpoolTrip trip,
    CarpoolingRequest request,
    Duration searchWindow
  ) {
    if (request.isArriveByRequest() || request.getRequestedDateTime() == null) {
      return true;
    }

    var edt = request.getRequestedDateTime();
    var tripStart = trip.startTime().toInstant();
    var tripEnd = trip.endTime().toInstant();

    if (tripEnd.isBefore(edt)) {
      return reject(trip, "tripEnd", tripEnd, "is before EDT", edt);
    }
    var slack = request.isEgressRequest() ? MAX_TOTAL_TRAVEL_TIME : MAX_WALK_TIME;
    var threshold = edt.plus(searchWindow).plus(slack);
    if (tripStart.isAfter(threshold)) {
      return reject(trip, "tripStart", tripStart, "is after LDT + slack", threshold);
    }
    return true;
  }

  private static boolean reject(
    CarpoolTrip trip,
    String field,
    Instant value,
    String reason,
    Instant bound
  ) {
    LOG.debug("Trip {} rejected: {} {} {} {}", trip.getId(), field, value, reason, bound);
    return false;
  }
}
