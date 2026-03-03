package org.opentripplanner.routing.algorithm.raptoradapter.router;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;
import java.util.stream.Collectors;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.routing.algorithm.raptoradapter.transit.RoutingOnBoardAccess;
import org.opentripplanner.routing.algorithm.raptoradapter.transit.request.OnBoardTripPatternSearch;
import org.opentripplanner.routing.api.request.TripLocation;
import org.opentripplanner.routing.api.request.TripOnDateReference;
import org.opentripplanner.transit.model.network.RoutingTripPattern;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.site.Station;
import org.opentripplanner.transit.model.site.StopLocation;
import org.opentripplanner.transit.model.timetable.Trip;
import org.opentripplanner.transit.service.TransitService;
import org.opentripplanner.utils.time.ServiceDateUtils;

/**
 * Resolves a {@link TripLocation} into a {@link ResolvedOnBoardAccess} by looking up the trip,
 * pattern, stop position, and trip schedule index in the transit model.
 */
public class OnBoardAccessResolver {

  private final TransitService transitService;

  public OnBoardAccessResolver(TransitService transitService) {
    this.transitService = transitService;
  }

  /**
   * Resolve the given trip location to a {@link ResolvedOnBoardAccess} containing the on-board
   * access and the service date.
   *
   * @param tripLocation the on-board location to resolve
   * @param patternSearch the search service for the active Raptor pattern index
   * @return a {@link ResolvedOnBoardAccess} for the given trip location
   * @throws IllegalArgumentException if the trip, stop, or stop position cannot be resolved
   */
  public ResolvedOnBoardAccess resolve(
    TripLocation tripLocation,
    OnBoardTripPatternSearch patternSearch
  ) {
    var resolvedTrip = resolveTrip(tripLocation.tripOnDateReference());
    var trip = resolvedTrip.trip();
    var serviceDate = resolvedTrip.serviceDate();
    var tripPattern = findPatternInRaptorData(trip, serviceDate, patternSearch);

    Integer targetSeconds = toSecondsSinceMidnight(
      tripLocation.scheduledDepartureTime(),
      serviceDate
    );
    int stopPosInPattern = findStopPosition(
      tripPattern,
      tripLocation.stopId(),
      trip,
      serviceDate,
      targetSeconds
    );

    RoutingTripPattern routingPattern = tripPattern.getRoutingTripPattern();
    int routeIndex = routingPattern.patternIndex();
    int raptorStopIndex = routingPattern.stopIndex(stopPosInPattern);

    var tripPatternForDates = patternSearch.findTripPatternForDates(routeIndex);
    int tripScheduleIndex = patternSearch.findTripScheduleIndex(
      tripPatternForDates,
      trip,
      serviceDate
    );

    var tripTimes = transitService
      .findTimetable(tripPattern, serviceDate)
      .getTripTimesWithScheduleFallback(trip);
    if (tripTimes == null) {
      throw new IllegalArgumentException(
        "Trip %s not found in timetable for pattern %s on date %s".formatted(
          trip.getId(),
          tripPattern.getId(),
          serviceDate
        )
      );
    }

    int boardingTime = tripTimes.getScheduledDepartureTime(stopPosInPattern);

    var access = new RoutingOnBoardAccess(
      routeIndex,
      tripScheduleIndex,
      stopPosInPattern,
      raptorStopIndex,
      boardingTime
    );

    return new ResolvedOnBoardAccess(access, serviceDate);
  }

  /**
   * Resolve the boarding time for an on-board trip location as an {@link Instant}. This uses
   * the transit service to look up the trip, pattern, and scheduled departure time — it does
   * not need the Raptor pattern index, so it can be called early in the pipeline to set the
   * request's dateTime before the search begins.
   */
  public Instant resolveBoardingDateTime(TripLocation tripLocation, ZoneId timeZone) {
    var resolvedTrip = resolveTrip(tripLocation.tripOnDateReference());
    var trip = resolvedTrip.trip();
    var serviceDate = resolvedTrip.serviceDate();

    var tripPattern = transitService.findPattern(trip, serviceDate);
    if (tripPattern == null) {
      tripPattern = transitService.findPattern(trip);
    }
    if (tripPattern == null) {
      throw new IllegalArgumentException(
        "No pattern found for trip %s on date %s".formatted(trip.getId(), serviceDate)
      );
    }

    Integer targetSeconds = toSecondsSinceMidnight(
      tripLocation.scheduledDepartureTime(),
      serviceDate
    );
    int stopPosInPattern = findStopPosition(
      tripPattern,
      tripLocation.stopId(),
      trip,
      serviceDate,
      targetSeconds
    );

    var tripTimes = transitService
      .findTimetable(tripPattern, serviceDate)
      .getTripTimesWithScheduleFallback(trip);
    if (tripTimes == null) {
      throw new IllegalArgumentException(
        "Trip %s not found in timetable for pattern %s on date %s".formatted(
          trip.getId(),
          tripPattern.getId(),
          serviceDate
        )
      );
    }

    int boardingTime = tripTimes.getScheduledDepartureTime(stopPosInPattern);

    var serviceDateStart = ServiceDateUtils.asStartOfService(serviceDate, timeZone);
    return serviceDateStart.plusSeconds(boardingTime).toInstant();
  }

  public record ResolvedOnBoardAccess(RoutingOnBoardAccess access, LocalDate serviceDate) {}

  /**
   * Find the trip pattern that exists in the Raptor transit data's pattern index. The realtime-
   * modified pattern (from {@code findPattern(trip, serviceDate)}) may have a new route index
   * not present in the Raptor data if the realtime updater hasn't processed that service date.
   * In that case, fall back to the base/static pattern.
   */
  private TripPattern findPatternInRaptorData(
    Trip trip,
    LocalDate serviceDate,
    OnBoardTripPatternSearch patternSearch
  ) {
    // Try the service-date-specific pattern first (may include realtime modifications)
    var tripPattern = transitService.findPattern(trip, serviceDate);
    if (tripPattern != null && patternSearch.isInPatternIndex(tripPattern)) {
      return tripPattern;
    }

    // Fall back to the base pattern (without service-date-specific realtime modifications)
    tripPattern = transitService.findPattern(trip);
    if (tripPattern != null && patternSearch.isInPatternIndex(tripPattern)) {
      return tripPattern;
    }

    throw new IllegalArgumentException(
      "No pattern for trip %s on date %s found in active Raptor data".formatted(
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

  private Set<FeedScopedId> resolveStopIds(FeedScopedId stopId) {
    var regularStop = transitService.getRegularStop(stopId);
    if (regularStop != null) {
      return Set.of(regularStop.getId());
    }
    Station station = transitService.getStation(stopId);
    if (station != null) {
      return station.getChildStops().stream().map(StopLocation::getId).collect(Collectors.toSet());
    }
    throw new IllegalArgumentException("Stop not found: " + stopId);
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
    FeedScopedId stopId,
    Trip trip,
    LocalDate serviceDate,
    Integer scheduledDepartureTime
  ) {
    var matchingIds = resolveStopIds(stopId);
    int firstMatch = -1;
    int matchCount = 0;

    for (int i = 0; i < tripPattern.numberOfStops(); i++) {
      if (matchingIds.contains(tripPattern.getStop(i).getId())) {
        if (firstMatch < 0) {
          firstMatch = i;
        }
        matchCount++;
      }
    }

    if (firstMatch < 0) {
      throw new IllegalArgumentException(
        "Stop %s not found in pattern for trip %s".formatted(stopId, trip.getId())
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
          stopId,
          matchCount,
          trip.getId()
        )
      );
    }

    return findStopPositionByDepartureTime(
      tripPattern,
      matchingIds,
      trip,
      serviceDate,
      scheduledDepartureTime
    );
  }

  /**
   * Find the stop position by matching the scheduled departure time (in seconds since midnight,
   * including day offset) among all occurrences of the given stop in the pattern.
   */
  private int findStopPositionByDepartureTime(
    TripPattern tripPattern,
    Set<FeedScopedId> matchingIds,
    Trip trip,
    LocalDate serviceDate,
    int targetSeconds
  ) {
    var tripTimes = transitService
      .findTimetable(tripPattern, serviceDate)
      .getTripTimesWithScheduleFallback(trip);
    if (tripTimes == null) {
      throw new IllegalArgumentException(
        "Trip %s not found in timetable for pattern %s on date %s".formatted(
          trip.getId(),
          tripPattern.getId(),
          serviceDate
        )
      );
    }

    for (int i = 0; i < tripPattern.numberOfStops(); i++) {
      if (!matchingIds.contains(tripPattern.getStop(i).getId())) {
        continue;
      }
      int depTime = tripTimes.getScheduledDepartureTime(i);
      if (depTime == targetSeconds) {
        return i;
      }
    }

    throw new IllegalArgumentException(
      "No stop %s with scheduled departure time %d found in pattern for trip %s".formatted(
        matchingIds,
        targetSeconds,
        trip.getId()
      )
    );
  }

  private record ResolvedTrip(Trip trip, LocalDate serviceDate) {}
}
