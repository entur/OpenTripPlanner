package org.opentripplanner.ext.carpooling.filter;

import java.time.Duration;
import java.time.Instant;
import org.opentripplanner.ext.carpooling.model.CarpoolTrip;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pre-filters carpool trip candidates for {@code arriveBy = true} requests. Depart-after requests
 * pass through unconditionally; their pre-filtering is handled by {@link DepartAfterTripFilter}.
 * <p>
 * A trip is rejected only when it is provably outside the passenger's arrival window, never on a
 * "this looks unlikely" basis. Trips that pass are still subject to expensive routing and a
 * tighter post-filter on the actual itinerary times.
 *
 * <h2>Variables</h2>
 *
 * <h3>Request window</h3>
 *
 * For {@code arriveBy = true}, the passenger's arrival window is derived from
 * {@code request.dateTime()} and the {@code searchWindow}:
 *
 * <ul>
 *   <li><strong>LAT</strong> — latest arrival time = {@code dateTime}. The passenger arrives at
 *       destination by LAT.</li>
 *   <li><strong>EAT</strong> — earliest arrival time = {@code dateTime − searchWindow}. The
 *       passenger does not arrive before EAT.</li>
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
 *       pickup/dropoff. A carpool dropoff at {@code EAT − W} still gives an arrival at or after
 *       EAT after a W-long walk to destination. <em>Concrete and known per request</em> — in
 *       principle this should come from
 *       {@code request.preferences().street().accessEgress().maxDuration().valueOf(WALK)}; today
 *       the constant is hardcoded.</li>
 *   <li><strong>{@code T} = {@link CarpoolTripFilter#MAX_TOTAL_TRAVEL_TIME}</strong> (max total
 *       passenger travel time, fallback) — used <em>only</em> for the access/too-early cell, where
 *       transit + egress between the carpool dropoff and the destination anchor is otherwise
 *       unbounded. {@code T} caps that unknown duration with a deliberately conservative number
 *       so the filter degrades to "almost a no-op" rather than producing false negatives.</li>
 * </ul>
 *
 * <h2>Rules (the {@code arriveBy = true} half)</h2>
 *
 * <pre>
 * | Leg type | Reject as TOO EARLY    | Reject as TOO LATE  |
 * |----------|------------------------|---------------------|
 * | Direct   | tripEnd   &lt; EAT − W    | tripStart &gt; LAT     |
 * | Access   | tripEnd   &lt; EAT − T    | tripStart &gt; LAT     |  (T fallback)
 * | Egress   | tripEnd   &lt; EAT − W    | tripStart &gt; LAT     |
 * </pre>
 *
 * <h3>Why these shapes</h3>
 *
 * <ul>
 *   <li><em>Too late</em> compares {@code tripStart} (earliest moment any pickup along the route
 *       can happen) against LAT. No slack is added because walks after dropoff only push arrival
 *       later, never earlier.</li>
 *   <li><em>Too early</em> compares {@code tripEnd} (latest moment any pickup or dropoff along
 *       the route can happen) against {@code EAT − slack}. {@code W} applies for direct & egress
 *       because a single dropoff walk separates the carpool from the destination; {@code T}
 *       replaces {@code W} for access because transit + egress between the carpool dropoff and
 *       the destination is otherwise unbounded.</li>
 * </ul>
 *
 * <h3>Behavior with missing inputs</h3>
 *
 * <ul>
 *   <li>{@code dateTime == null}: pass-through (no filtering possible).</li>
 * </ul>
 */
public class ArriveByTripFilter implements CarpoolTripFilter {

  private static final Logger LOG = LoggerFactory.getLogger(ArriveByTripFilter.class);

  @Override
  public boolean isCandidateTrip(
    CarpoolTrip trip,
    CarpoolingRequest request,
    Duration searchWindow
  ) {
    if (!request.isArriveByRequest() || request.getRequestedDateTime() == null) {
      return true;
    }

    var lat = request.getRequestedDateTime();
    var tripStart = trip.startTime().toInstant();
    var tripEnd = trip.endTime().toInstant();

    if (tripStart.isAfter(lat)) {
      return reject(trip, "tripStart", tripStart, "is after LAT", lat);
    }
    var slack = request.isAccessRequest() ? MAX_TOTAL_TRAVEL_TIME : MAX_WALK_TIME;
    var threshold = lat.minus(searchWindow).minus(slack);
    if (tripEnd.isBefore(threshold)) {
      return reject(trip, "tripEnd", tripEnd, "is before EAT − slack", threshold);
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
