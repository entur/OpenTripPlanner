package org.opentripplanner.ext.carpooling.filter;

import java.time.Duration;
import java.time.Instant;
import org.opentripplanner.ext.carpooling.model.CarpoolTrip;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pre-filters trips based on necessary (but not sufficient) time compatibility with the passenger
 * request.
 * <p>
 * Because passengers board and alight somewhere along the driver's route rather than at its
 * endpoints, this filter is intentionally loose — only trips that are provably impossible are
 * discarded. Exact enforcement is delegated to post-filters on the complete itinerary:
 * <ul>
 *   <li><strong>Arrive-by:</strong> accepts trips whose start time is at or before the requested
 *       arrival deadline (trip.startTime &le; T). A driver who has not yet started cannot drop off
 *       the passenger in time. Tight enforcement is done by {@link ArriveByFilter}.</li>
 *   <li><strong>Depart-after:</strong> accepts trips still running at the requested departure time
 *       (trip.endTime &ge; T) that also start within the search window (trip.startTime &le;
 *       T&nbsp;+&nbsp;searchWindow). A trip that has already finished, or that starts too far in
 *       the future, cannot serve the passenger. Tight enforcement is done by
 *       {@link DepartAfterFilter}.</li>
 * </ul>
 */
public class TimeBasedFilter implements TripFilter {

  private static final Logger LOG = LoggerFactory.getLogger(TimeBasedFilter.class);

  @Override
  public boolean accepts(CarpoolTrip trip, CarpoolingRequest request, Duration searchWindow) {
    if (request.getRequestedDateTime() == null) {
      return true;
    }

    var requestedDateTime = request.getRequestedDateTime();
    if (request.isArriveByRequest()) {
      return couldArriveByDeadline(trip.getId(), requestedDateTime, trip.startTime().toInstant());
    } else {
      return couldDepartAfterTime(
        trip.getId(),
        requestedDateTime,
        trip.startTime().toInstant(),
        trip.endTime() != null ? trip.endTime().toInstant() : null,
        searchWindow
      );
    }
  }

  /**
   * Accepts trips whose driver has started at or before the passenger's arrival deadline —
   * i.e. trip.startTime &le; requestedArrivalTime.
   * <p>
   * This is a necessary condition only. A trip whose driver continues past the passenger's dropoff
   * is still viable. Tight enforcement (itinerary.endTime &le; T) is done by
   * {@link ArriveByFilter}.
   */
  private boolean couldArriveByDeadline(
    Object tripId,
    Instant requestedArrivalTime,
    Instant tripStartTime
  ) {
    boolean withinWindow = !tripStartTime.isAfter(requestedArrivalTime);

    if (!withinWindow) {
      LOG.debug(
        "Trip {} rejected by time filter: trip starts at {}, which is after the arrive-by deadline {}",
        tripId,
        tripStartTime,
        requestedArrivalTime
      );
    }
    return withinWindow;
  }

  /**
   * Accepts trips that are still running at the requested departure time and start within the
   * search window — i.e. trip.endTime &ge; T and trip.startTime &le; T + searchWindow.
   * <p>
   * Using trip.endTime as the lower bound (rather than trip.startTime) allows mid-route pickups
   * for trips that were already underway when the passenger wants to depart. The upper bound
   * avoids surfacing trips that start far in the future. Tight enforcement (itinerary.startTime
   * &ge; T) is done by {@link DepartAfterFilter}.
   */
  private boolean couldDepartAfterTime(
    Object tripId,
    Instant requestedDepartureTime,
    Instant tripStartTime,
    Instant tripEndTime,
    Duration searchWindow
  ) {
    boolean tripStillRunning = tripEndTime == null || !tripEndTime.isBefore(requestedDepartureTime);
    boolean startsWithinWindow =
      searchWindow == null || !tripStartTime.isAfter(requestedDepartureTime.plus(searchWindow));
    boolean withinWindow = tripStillRunning && startsWithinWindow;

    if (!withinWindow) {
      LOG.debug(
        "Trip {} rejected by time filter: trip runs {}–{}, passenger requests depart-after {} (window={})",
        tripId,
        tripStartTime,
        tripEndTime,
        requestedDepartureTime,
        searchWindow
      );
    }
    return withinWindow;
  }

  @Override
  public boolean acceptsAccessEgress(
    CarpoolTrip trip,
    CarpoolingRequest request,
    Duration searchWindow
  ) {
    return accepts(trip, request, searchWindow);
  }
}
