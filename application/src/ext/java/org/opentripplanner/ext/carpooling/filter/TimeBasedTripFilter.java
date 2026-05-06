package org.opentripplanner.ext.carpooling.filter;

import java.time.Duration;
import java.time.Instant;
import org.opentripplanner.ext.carpooling.model.CarpoolTrip;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pre-filters carpool trip candidates based on time compatibility with the passenger request.
 * <p>
 * A trip is rejected only when it is provably outside the passenger's window, never on a
 * "this looks unlikely" basis. Trips that pass are still subject to expensive routing and a
 * tighter post-filter on the actual itinerary times.
 *
 * <h2>Variables</h2>
 *
 * <h3>Request window</h3>
 *
 * The passenger's window is derived from {@code request.dateTime()} and the {@code searchWindow}.
 * Which two of EDT/LDT/EAT/LAT exist depends on {@code arriveBy}:
 *
 * <ul>
 *   <li><strong>EDT</strong> — earliest departure time. Defined when {@code arriveBy = false} as
 *       {@code dateTime}. The passenger does not depart from origin before EDT.</li>
 *   <li><strong>LDT</strong> — latest departure time. Defined when {@code arriveBy = false} as
 *       {@code dateTime + searchWindow}. The passenger departs by LDT.</li>
 *   <li><strong>LAT</strong> — latest arrival time. Defined when {@code arriveBy = true} as
 *       {@code dateTime}. The passenger arrives at destination by LAT.</li>
 *   <li><strong>EAT</strong> — earliest arrival time. Defined when {@code arriveBy = true} as
 *       {@code dateTime − searchWindow}. The passenger does not arrive before EAT.</li>
 * </ul>
 *
 * <h3>Slack constants</h3>
 *
 * The carpool window is {@code [trip.startTime, trip.endTime]} but the passenger boards and
 * alights somewhere in the middle. Two slacks pad the request window outward so trips that could
 * still pick up or drop off a passenger inside their window are not rejected:
 *
 * <ul>
 *   <li><strong>{@code W} = {@link CarpoolTripFilter#MAX_WALK_TIME}</strong> (max walk time) —
 *       upper bound on how long the passenger walks between origin/destination and the carpool
 *       pickup/dropoff. Used wherever a single walk segment shifts the relevant bound by a known,
 *       bounded amount: a passenger leaving by LDT can be at the pickup as late as
 *       {@code LDT + W}; a carpool that drops off by {@code EAT − W} still gives an arrival at or
 *       after EAT after a W-long walk to destination. <em>Concrete and known per request</em> —
 *       in principle this should come from
 *       {@code request.preferences().street().accessEgress().maxDuration().valueOf(WALK)}; today
 *       the constant is hardcoded.</li>
 *   <li><strong>{@code T} = {@link CarpoolTripFilter#EGRESS_SLACK}</strong> (max total
 *       passenger travel time, fallback) — used <em>only</em> in the two cells where neither the
 *       request window nor {@code W} can produce a real bound: <em>access / arriveBy=true / too
 *       early</em> and <em>egress / arriveBy=false / too late</em>. In both cases the carpool
 *       sits on the opposite end of the journey from the request anchor, separated from it by a
 *       transit ride of unknown duration. {@code T} caps that unknown duration with a
 *       deliberately conservative number so the filter degrades to "almost a no-op" in those
 *       cells rather than producing false negatives. The constant is named {@code EGRESS_SLACK}
 *       on the interface for historical reasons; semantically it is the total-travel cap.</li>
 * </ul>
 *
 * <h2>Rules</h2>
 *
 * <pre>
 * | Leg type | arriveBy | Reject as TOO EARLY    | Reject as TOO LATE     |
 * |----------|----------|------------------------|------------------------|
 * | Direct   | false    | tripEnd   &lt; EDT        | tripStart &gt; LDT + W    |
 * | Direct   | true     | tripEnd   &lt; EAT − W    | tripStart &gt; LAT        |
 * | Access   | false    | tripEnd   &lt; EDT        | tripStart &gt; LDT + W    |
 * | Access   | true     | tripEnd   &lt; EAT − T    | tripStart &gt; LAT        |  (T fallback)
 * | Egress   | false    | tripEnd   &lt; EDT        | tripStart &gt; LDT + T    |  (T fallback)
 * | Egress   | true     | tripEnd   &lt; EAT − W    | tripStart &gt; LAT        |
 * </pre>
 *
 * <h3>Why these shapes</h3>
 *
 * <ul>
 *   <li><em>Too late</em> compares {@code tripStart} (earliest moment any pickup along the route
 *       can happen) against the latest the passenger could be at that pickup. {@code W} is added
 *       when the request anchors departure (arriveBy=false) and a walk pads the bound; {@code T}
 *       replaces {@code W} on the egress/false cell because access + transit between the anchor
 *       and the egress carpool is otherwise unbounded.</li>
 *   <li><em>Too early</em> compares {@code tripEnd} (latest moment any pickup or dropoff along
 *       the route can happen) against the earliest the passenger could be there. {@code W} is
 *       subtracted when a single dropoff walk separates the carpool from the destination anchor
 *       (direct & egress with arriveBy=true); {@code T} replaces {@code W} on the access/true
 *       cell because transit + egress between the carpool dropoff and the anchor is otherwise
 *       unbounded.</li>
 *   <li>The four cells with neither {@code W} nor {@code T} are the cases where the carpool is on
 *       the same side as the anchor and no walk or padding can move the bound.</li>
 * </ul>
 *
 * <h3>Behavior with missing inputs</h3>
 *
 * <ul>
 *   <li>{@code dateTime == null}: pass-through (no filtering possible).</li>
 *   <li>{@code searchWindow == null}: the bound that depends on the search window (too-late for
 *       arriveBy=false, too-early for arriveBy=true) is skipped. The opposite-side bound still
 *       applies because it depends only on {@code dateTime}.</li>
 * </ul>
 */
public class TimeBasedTripFilter implements CarpoolTripFilter {

  private static final Logger LOG = LoggerFactory.getLogger(TimeBasedTripFilter.class);

  @Override
  public boolean isCandidateTrip(
    CarpoolTrip trip,
    CarpoolingRequest request,
    Duration searchWindow
  ) {
    var dateTime = request.getRequestedDateTime();
    if (dateTime == null) {
      return true;
    }

    var tripStart = trip.startTime().toInstant();
    var tripEnd = trip.endTime().toInstant();

    return request.isArriveByRequest()
      ? acceptsArriveBy(trip, request, searchWindow, dateTime, tripStart, tripEnd)
      : acceptsDepartAfter(trip, request, searchWindow, dateTime, tripStart, tripEnd);
  }

  /**
   * Rules for {@code arriveBy = true}: dateTime is LAT; EAT = LAT − searchWindow.
   * <ul>
   *   <li>Too late: {@code tripStart > LAT} for all leg types.</li>
   *   <li>Too early: {@code tripEnd < EAT − slack} where slack is {@code T} for access (transit +
   *       egress on the destination side is unbounded) and {@code W} otherwise.</li>
   * </ul>
   */
  private static boolean acceptsArriveBy(
    CarpoolTrip trip,
    CarpoolingRequest request,
    Duration searchWindow,
    Instant lat,
    Instant tripStart,
    Instant tripEnd
  ) {
    if (tripStart.isAfter(lat)) {
      return reject(trip, "tripStart", tripStart, "is after LAT", lat);
    }
    if (searchWindow != null) {
      var slack = request.isAccessRequest() ? EGRESS_SLACK : MAX_WALK_TIME;
      var threshold = lat.minus(searchWindow).minus(slack);
      if (tripEnd.isBefore(threshold)) {
        return reject(trip, "tripEnd", tripEnd, "is before EAT − slack", threshold);
      }
    }
    return true;
  }

  /**
   * Rules for {@code arriveBy = false}: dateTime is EDT; LDT = EDT + searchWindow.
   * <ul>
   *   <li>Too early: {@code tripEnd < EDT} for all leg types.</li>
   *   <li>Too late: {@code tripStart > LDT + slack} where slack is {@code T} for egress (access +
   *       transit on the origin side is unbounded) and {@code W} otherwise.</li>
   * </ul>
   */
  private static boolean acceptsDepartAfter(
    CarpoolTrip trip,
    CarpoolingRequest request,
    Duration searchWindow,
    Instant edt,
    Instant tripStart,
    Instant tripEnd
  ) {
    if (tripEnd.isBefore(edt)) {
      return reject(trip, "tripEnd", tripEnd, "is before EDT", edt);
    }
    if (searchWindow != null) {
      var slack = request.isEgressRequest() ? EGRESS_SLACK : MAX_WALK_TIME;
      var threshold = edt.plus(searchWindow).plus(slack);
      if (tripStart.isAfter(threshold)) {
        return reject(trip, "tripStart", tripStart, "is after LDT + slack", threshold);
      }
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
