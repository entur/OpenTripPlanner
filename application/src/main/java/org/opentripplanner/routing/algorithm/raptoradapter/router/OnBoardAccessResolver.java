package org.opentripplanner.routing.algorithm.raptoradapter.router;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.raptor.api.model.RaptorOnBoardAccess;
import org.opentripplanner.routing.algorithm.raptoradapter.transit.RoutingOnBoardAccess;
import org.opentripplanner.routing.algorithm.raptoradapter.transit.TripPatternForDate;
import org.opentripplanner.routing.algorithm.raptoradapter.transit.request.TripPatternForDates;
import org.opentripplanner.routing.api.request.TripLocation;
import org.opentripplanner.routing.api.request.TripOnDateReference;
import org.opentripplanner.transit.model.network.RoutingTripPattern;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.site.StopLocation;
import org.opentripplanner.transit.model.timetable.Trip;
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
    var tripPattern = findPatternInRaptorData(trip, serviceDate, patternIndex);

    var stop = resolveStop(tripLocation.stopId());
    Integer targetSeconds = toSecondsSinceMidnight(
      tripLocation.scheduledDepartureTime(),
      serviceDate
    );
    int stopPosInPattern = findStopPosition(tripPattern, stop, trip, targetSeconds);

    RoutingTripPattern routingPattern = tripPattern.getRoutingTripPattern();
    int routeIndex = routingPattern.patternIndex();
    int raptorStopIndex = routingPattern.stopIndex(stopPosInPattern);

    var tripPatternForDates = findTripPatternForDates(routeIndex, patternIndex);
    int tripScheduleIndex = findTripScheduleIndex(tripPatternForDates, trip, serviceDate);

    return new RoutingOnBoardAccess(
      routeIndex,
      tripScheduleIndex,
      stopPosInPattern,
      raptorStopIndex
    );
  }

  /**
   * Find the trip pattern that exists in the Raptor transit data's pattern index. The realtime-
   * modified pattern (from {@code findPattern(trip, serviceDate)}) may have a new route index
   * not present in the Raptor data if the realtime updater hasn't processed that service date.
   * In that case, fall back to the base/static pattern.
   */
  private TripPattern findPatternInRaptorData(
    Trip trip,
    LocalDate serviceDate,
    List<TripPatternForDates> patternIndex
  ) {
    // Try the service-date-specific pattern first (may include realtime modifications)
    var tripPattern = transitService.findPattern(trip, serviceDate);
    if (tripPattern != null && isInPatternIndex(tripPattern, patternIndex)) {
      return tripPattern;
    }

    // Fall back to the base pattern (without service-date-specific realtime modifications)
    tripPattern = transitService.findPattern(trip);
    if (tripPattern != null && isInPatternIndex(tripPattern, patternIndex)) {
      return tripPattern;
    }

    // Last resort: search the pattern index directly for a pattern containing this trip.
    // This handles the case where realtime updates create new patterns with route indices
    // not present in the Raptor data (e.g., the SIRI updater hasn't processed the service date).
    return searchPatternIndex(trip, serviceDate, patternIndex);
  }

  private boolean isInPatternIndex(TripPattern pattern, List<TripPatternForDates> patternIndex) {
    int routeIndex = pattern.getRoutingTripPattern().patternIndex();
    return routeIndex < patternIndex.size() && patternIndex.get(routeIndex) != null;
  }

  private TripPattern searchPatternIndex(
    Trip trip,
    LocalDate serviceDate,
    List<TripPatternForDates> patternIndex
  ) {
    for (TripPatternForDates tpfd : patternIndex) {
      if (tpfd == null) {
        continue;
      }
      var dateIterator = tpfd.tripPatternForDatesIndexIterator(true);
      while (dateIterator.hasNext()) {
        int dayIndex = dateIterator.next();
        TripPatternForDate tpForDate = tpfd.tripPatternForDate(dayIndex);
        if (tpForDate.getServiceDate().equals(serviceDate)) {
          for (int j = 0; j < tpForDate.numberOfTripSchedules(); j++) {
            if (tpForDate.getTripTimes(j).getTrip().getId().equals(trip.getId())) {
              // TODO getPattern is deprecated
              return tpfd.getTripPattern().getPattern();
            }
          }
        }
      }
    }
    throw new IllegalArgumentException(
      "No pattern containing trip %s on date %s found in active Raptor data".formatted(
        trip.getId(),
        serviceDate
      )
    );
  }

  private ResolvedTrip resolveTrip(TripOnDateReference reference) {
    if (reference.tripOnServiceDateId() != null) {
      var tripOnServiceDate = transitService.getTripOnServiceDate(reference.tripOnServiceDateId());
      if (tripOnServiceDate == null) {
        throw new IllegalArgumentException(
          "TripOnServiceDate not found: " + reference.tripOnServiceDateId()
        );
      }
      return new ResolvedTrip(tripOnServiceDate.getTrip(), tripOnServiceDate.getServiceDate());
    } else if (reference.tripIdOnServiceDate() != null) {
      var tripIdAndDate = reference.tripIdOnServiceDate();
      var trip = transitService.getTrip(tripIdAndDate.tripId());
      if (trip == null) {
        throw new IllegalArgumentException("Trip not found: " + tripIdAndDate.tripId());
      }
      return new ResolvedTrip(trip, tripIdAndDate.serviceDate());
    }

    throw new IllegalArgumentException(
      "Either tripOnServiceDateId or tripIdOnServiceDate must be set on TripOnDateReference"
    );
  }

  private StopLocation resolveStop(FeedScopedId stopId) {
    Collection<StopLocation> stops = transitService.findStopOrChildStops(stopId);
    if (stops.isEmpty()) {
      throw new IllegalArgumentException("Stop not found: " + stopId);
    }
    return stops.iterator().next();
  }

  /**
   * Convert epoch milliseconds to seconds since midnight of the given service date in the transit
   * system's timezone. Returns null if epochMillis is null.
   */
  private Integer toSecondsSinceMidnight(Long epochMillis, LocalDate serviceDate) {
    if (epochMillis == null) {
      return null;
    }
    ZoneId timeZone = transitService.getTimeZone();
    long serviceDayMidnightEpochSecond = serviceDate.atStartOfDay(timeZone).toEpochSecond();
    return (int) (epochMillis / 1000 - serviceDayMidnightEpochSecond);
  }

  /**
   * Find the stop position in the pattern. If the stop appears more than once and no
   * {@code scheduledDepartureTime} is provided, an error is thrown. When a
   * {@code scheduledDepartureTime} is provided, it is used to disambiguate among multiple
   * occurrences of the same stop.
   */
  private int findStopPosition(
    TripPattern tripPattern,
    StopLocation stop,
    Trip trip,
    Integer scheduledDepartureTime
  ) {
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

    if (matchCount == 1) {
      return firstMatch;
    }

    // Multiple occurrences — need scheduledDepartureTime to disambiguate
    if (scheduledDepartureTime == null) {
      throw new IllegalArgumentException(
        ("Stop %s appears %d times in pattern for trip %s. " +
          "Use stopIdAndScheduledDepartureTime to disambiguate.").formatted(
          stop.getId(),
          matchCount,
          trip.getId()
        )
      );
    }

    return findStopPositionByDepartureTime(tripPattern, stop, trip, scheduledDepartureTime);
  }

  /**
   * Find the stop position by matching the scheduled departure time (in seconds since midnight,
   * including day offset) among all occurrences of the given stop in the pattern.
   */
  private int findStopPositionByDepartureTime(
    TripPattern tripPattern,
    StopLocation stop,
    Trip trip,
    int targetSeconds
  ) {
    var tripTimes = tripPattern.getScheduledTimetable().getTripTimes(trip);
    if (tripTimes == null) {
      throw new IllegalArgumentException(
        "Trip %s not found in scheduled timetable for pattern %s".formatted(
          trip.getId(),
          tripPattern.getId()
        )
      );
    }

    for (int i = 0; i < tripPattern.numberOfStops(); i++) {
      if (!tripPattern.getStop(i).getId().equals(stop.getId())) {
        continue;
      }
      int depTime = tripTimes.getScheduledDepartureTime(i);
      if (depTime == targetSeconds) {
        return i;
      }
    }

    throw new IllegalArgumentException(
      "No stop %s with scheduled departure time %d found in pattern for trip %s".formatted(
        stop.getId(),
        targetSeconds,
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
}
