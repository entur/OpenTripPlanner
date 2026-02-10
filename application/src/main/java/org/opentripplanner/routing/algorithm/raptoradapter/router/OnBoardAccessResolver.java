package org.opentripplanner.routing.algorithm.raptoradapter.router;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import javax.annotation.Nullable;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.raptor.api.model.RaptorOnBoardAccess;
import org.opentripplanner.routing.algorithm.raptoradapter.transit.TripPatternForDate;
import org.opentripplanner.routing.algorithm.raptoradapter.transit.request.TripPatternForDates;
import org.opentripplanner.routing.api.request.TripLocation;
import org.opentripplanner.routing.api.request.TripOnDateReference;
import org.opentripplanner.transit.model.network.RoutingTripPattern;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.site.StopLocation;
import org.opentripplanner.transit.model.timetable.Trip;
import org.opentripplanner.transit.model.timetable.TripOnServiceDate;
import org.opentripplanner.transit.service.TransitService;

/**
 * Resolves a {@link TripLocation} into a {@link RaptorOnBoardAccess} by looking up the trip,
 * pattern, stop position, and trip schedule index in the transit model.
 */
public class OnBoardAccessResolver {

  private final TransitService transitService;

  public OnBoardAccessResolver(TransitService transitService) {
    this.transitService = transitService;
  }

  /**
   * Resolve the given trip location to a Raptor on-board access.
   *
   * @param tripLocation the on-board location to resolve
   * @param patternIndex the list of {@link TripPatternForDates} indexed by route index, as
   *                     constructed for the current request
   * @return a {@link RaptorOnBoardAccess} for the given trip location
   * @throws IllegalArgumentException if the trip, stop, or stop position cannot be resolved
   */
  public RaptorOnBoardAccess resolve(
    TripLocation tripLocation,
    List<TripPatternForDates> patternIndex
  ) {
    var resolved = resolveTrip(tripLocation.tripOnDateReference());
    var trip = resolved.trip();
    var serviceDate = resolved.serviceDate();
    var tripPattern = transitService.findPattern(trip, serviceDate);

    if (tripPattern == null) {
      throw new IllegalArgumentException(
        "No trip pattern found for trip %s on date %s".formatted(trip.getId(), serviceDate)
      );
    }

    var stop = resolveStop(tripLocation.stopId());
    int stopPosInPattern = findStopPosition(
      tripPattern,
      stop,
      trip,
      serviceDate,
      tripLocation.scheduledDepartureTime()
    );

    RoutingTripPattern routingPattern = tripPattern.getRoutingTripPattern();
    int routeIndex = routingPattern.patternIndex();
    int raptorStopIndex = routingPattern.stopIndex(stopPosInPattern);

    var tripPatternForDates = findTripPatternForDates(routeIndex, patternIndex);
    int tripScheduleIndex = findTripScheduleIndex(tripPatternForDates, trip, serviceDate);

    return new ResolvedOnBoardAccess(
      routeIndex,
      tripScheduleIndex,
      stopPosInPattern,
      raptorStopIndex
    );
  }

  private ResolvedTrip resolveTrip(TripOnDateReference reference) {
    if (reference.tripOnDateId() != null) {
      TripOnServiceDate tripOnServiceDate = transitService.getTripOnServiceDate(
        reference.tripOnDateId()
      );
      if (tripOnServiceDate == null) {
        throw new IllegalArgumentException(
          "TripOnServiceDate not found: " + reference.tripOnDateId()
        );
      }
      return new ResolvedTrip(tripOnServiceDate.getTrip(), tripOnServiceDate.getServiceDate());
    } else {
      var tripIdAndDate = reference.tripIdOnServiceDate();
      Trip trip = transitService.getTrip(tripIdAndDate.tripId());
      if (trip == null) {
        throw new IllegalArgumentException("Trip not found: " + tripIdAndDate.tripId());
      }
      return new ResolvedTrip(trip, tripIdAndDate.serviceDate());
    }
  }

  private StopLocation resolveStop(FeedScopedId stopId) {
    Collection<StopLocation> stops = transitService.findStopOrChildStops(stopId);
    if (stops.isEmpty()) {
      throw new IllegalArgumentException("Stop not found: " + stopId);
    }
    return stops.iterator().next();
  }

  /**
   * Find the stop position in the pattern. For ring lines where a stop appears more than once, use
   * the scheduled departure time to disambiguate.
   */
  private int findStopPosition(
    TripPattern tripPattern,
    StopLocation stop,
    Trip trip,
    LocalDate serviceDate,
    @Nullable LocalDateTime scheduledDepartureTime
  ) {
    // Collect all positions where this stop appears
    int firstMatch = -1;
    int matchCount = 0;

    for (int i = 0; i < tripPattern.numberOfStops(); i++) {
      if (tripPattern.getStop(i).getId().equals(stop.getId())) {
        if (firstMatch < 0) {
          firstMatch = i;
        }
        matchCount++;
      }
    }

    if (firstMatch < 0) {
      throw new IllegalArgumentException(
        "Stop %s not found in pattern for trip %s".formatted(stop.getId(), trip.getId())
      );
    }

    // If the stop only appears once, no disambiguation needed
    if (matchCount == 1) {
      return firstMatch;
    }

    // Ring line: use scheduledDepartureTime to disambiguate
    if (scheduledDepartureTime == null) {
      throw new IllegalArgumentException(
        "Stop %s appears %d times in pattern for trip %s. scheduledDepartureTime is required to disambiguate.".formatted(
          stop.getId(),
          matchCount,
          trip.getId()
        )
      );
    }

    var tripTimes = tripPattern.getScheduledTimetable().getTripTimes(trip);
    if (tripTimes == null) {
      throw new IllegalArgumentException(
        "Trip %s not found in scheduled timetable for pattern %s".formatted(
          trip.getId(),
          tripPattern.getId()
        )
      );
    }

    int targetSeconds = scheduledDepartureTime.toLocalTime().toSecondOfDay();

    for (int i = 0; i < tripPattern.numberOfStops(); i++) {
      if (
        tripPattern.getStop(i).getId().equals(stop.getId()) &&
        tripTimes.getDepartureTime(i) == targetSeconds
      ) {
        return i;
      }
    }

    throw new IllegalArgumentException(
      "Could not find stop %s with scheduled departure time %s in pattern for trip %s".formatted(
        stop.getId(),
        scheduledDepartureTime,
        trip.getId()
      )
    );
  }

  private TripPatternForDates findTripPatternForDates(
    int routeIndex,
    List<TripPatternForDates> patternIndex
  ) {
    if (routeIndex >= patternIndex.size() || patternIndex.get(routeIndex) == null) {
      throw new IllegalArgumentException(
        "Route index %d not found in active patterns for this search".formatted(routeIndex)
      );
    }
    return patternIndex.get(routeIndex);
  }

  /**
   * Find the global trip schedule index within the {@link TripPatternForDates} for a specific trip
   * on a specific service date.
   */
  private int findTripScheduleIndex(
    TripPatternForDates tripPatternForDates,
    Trip trip,
    LocalDate serviceDate
  ) {
    int globalIndex = 0;
    var dateIterator = tripPatternForDates.tripPatternForDatesIndexIterator(true);

    while (dateIterator.hasNext()) {
      int dayIndex = dateIterator.next();
      TripPatternForDate tpfd = tripPatternForDates.tripPatternForDate(dayIndex);

      if (tpfd.getServiceDate().equals(serviceDate)) {
        for (int i = 0; i < tpfd.numberOfTripSchedules(); i++) {
          if (tpfd.getTripTimes(i).getTrip().getId().equals(trip.getId())) {
            return globalIndex + i;
          }
        }
        throw new IllegalArgumentException(
          "Trip %s not found in pattern for service date %s".formatted(trip.getId(), serviceDate)
        );
      }
      globalIndex += tpfd.numberOfTripSchedules();
    }

    throw new IllegalArgumentException(
      "Service date %s not found in active patterns for trip %s".formatted(
        serviceDate,
        trip.getId()
      )
    );
  }

  private record ResolvedTrip(Trip trip, LocalDate serviceDate) {}

  private record ResolvedOnBoardAccess(
    int routeIndex,
    int tripScheduleIndex,
    int stopPositionInPattern,
    int stop
  ) implements RaptorOnBoardAccess {
    @Override
    public int c1() {
      return 0;
    }

    @Override
    public String toString() {
      return asString(true, true, null);
    }
  }
}
