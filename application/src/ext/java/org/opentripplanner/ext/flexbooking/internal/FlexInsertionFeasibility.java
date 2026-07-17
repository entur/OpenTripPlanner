package org.opentripplanner.ext.flexbooking.internal;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Optional;
import org.opentripplanner.ext.flex.trip.UnscheduledTrip;
import org.opentripplanner.transit.model.timetable.booking.RoutingBookingInfo;
import org.opentripplanner.utils.time.ServiceDateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pure time feasibility checks for inserting a new passenger into a flex vehicle's booked tour.
 * <p>
 * The vehicle is on a committed schedule, so an insertion yields <em>absolute</em> pickup and
 * dropoff times which are never time-shifted to the passenger's request. A candidate is feasible
 * when the passenger's full journey (including snap walks) respects the requested
 * depart-after/arrive-by time, both times lie within the trip's static NeTEx stop time windows,
 * and the booking rules allow boarding.
 */
public final class FlexInsertionFeasibility {

  private static final Logger LOG = LoggerFactory.getLogger(FlexInsertionFeasibility.class);

  private FlexInsertionFeasibility() {}

  /**
   * The absolute passenger times of a feasible insertion: the moment the vehicle arrives at the
   * pickup and the moment it reaches the dropoff.
   */
  public record TimedInsertion(ZonedDateTime pickupTime, ZonedDateTime dropoffTime) {}

  /**
   * Evaluates the insertion described by the tour start plus the routed durations, returning the
   * absolute passenger times when feasible and empty otherwise.
   *
   * @param tourStartTime the tour's absolute start time (departure from the first stop)
   * @param durationUntilPickupArrival routed duration from tour start to arrival at the pickup
   * @param passengerRideDuration routed duration from pickup arrival to dropoff (incl. dwell)
   * @param trip the static flex trip, for the NeTEx stop time windows
   * @param boardStopPosition the passenger's board position in the trip's pattern
   * @param alightStopPosition the passenger's alight position in the trip's pattern
   * @param startOfService start of service for the tour's service date, the zero point of the
   *        trip's stop time windows
   * @param requestedTime the requested departure (or arrival, when {@code arriveBy}) time
   * @param arriveBy whether {@code requestedTime} is an arrive-by time
   * @param walkToPickup duration of the passenger's walk from the origin to the pickup
   * @param walkFromDropoff duration of the passenger's walk from the dropoff to the destination
   * @param bookingInfo the routing booking rules for the board position
   */
  public static Optional<TimedInsertion> evaluate(
    ZonedDateTime tourStartTime,
    Duration durationUntilPickupArrival,
    Duration passengerRideDuration,
    UnscheduledTrip trip,
    int boardStopPosition,
    int alightStopPosition,
    ZonedDateTime startOfService,
    Instant requestedTime,
    boolean arriveBy,
    Duration walkToPickup,
    Duration walkFromDropoff,
    RoutingBookingInfo bookingInfo
  ) {
    var pickupTime = tourStartTime.plus(durationUntilPickupArrival);
    var dropoffTime = pickupTime.plus(passengerRideDuration);

    // The vehicle cannot wait: the passenger must be able to reach the pickup before the vehicle
    // arrives (depart-after), or the whole journey must end by the requested time (arrive-by).
    if (arriveBy) {
      if (dropoffTime.plus(walkFromDropoff).toInstant().isAfter(requestedTime)) {
        LOG.debug("Insertion rejected: journey ends after the requested arrive-by time");
        return Optional.empty();
      }
    } else {
      if (pickupTime.minus(walkToPickup).toInstant().isBefore(requestedTime)) {
        LOG.debug("Insertion rejected: vehicle reaches the pickup before the passenger can");
        return Optional.empty();
      }
    }

    int pickupSeconds = ServiceDateUtils.secondsSinceStartOfTime(
      startOfService,
      pickupTime.toInstant()
    );
    int dropoffSeconds = ServiceDateUtils.secondsSinceStartOfTime(
      startOfService,
      dropoffTime.toInstant()
    );
    if (
      pickupSeconds < trip.earliestDepartureTime(boardStopPosition) ||
      pickupSeconds > trip.latestArrivalTime(boardStopPosition) ||
      dropoffSeconds < trip.earliestDepartureTime(alightStopPosition) ||
      dropoffSeconds > trip.latestArrivalTime(alightStopPosition)
    ) {
      LOG.debug("Insertion rejected: pickup or dropoff outside the trip's stop time windows");
      return Optional.empty();
    }

    if (bookingInfo.exceedsMinimumBookingNotice(pickupSeconds)) {
      LOG.debug("Insertion rejected: pickup violates the minimum booking notice");
      return Optional.empty();
    }

    return Optional.of(new TimedInsertion(pickupTime, dropoffTime));
  }
}
