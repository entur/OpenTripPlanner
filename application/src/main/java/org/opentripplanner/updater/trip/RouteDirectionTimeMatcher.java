package org.opentripplanner.updater.trip;

import gnu.trove.set.TIntSet;
import java.time.LocalDate;
import java.util.Objects;
import org.opentripplanner.transit.model.framework.Result;
import org.opentripplanner.transit.model.network.Route;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.timetable.TripTimes;
import org.opentripplanner.transit.service.TransitService;
import org.opentripplanner.updater.spi.UpdateError;
import org.opentripplanner.updater.trip.model.ParsedTripUpdate;
import org.opentripplanner.updater.trip.model.TripReference;
import org.opentripplanner.utils.time.TimeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * GTFS-RT style fuzzy trip matcher that matches trips by route, direction, and start time.
 * <p>
 * This matcher is used when the real-time feed provides route/direction/start time
 * but not a direct trip ID. It searches for a matching trip in the schedule.
 * <p>
 * Matching algorithm:
 * <ol>
 *   <li>Look up route by ID from {@link TripReference#routeId()}</li>
 *   <li>Get all patterns for the route</li>
 *   <li>Filter patterns by direction from {@link TripReference#direction()}</li>
 *   <li>Find trip with matching departure time at first stop</li>
 *   <li>Validate service runs on the given date</li>
 *   <li>Handle midnight-spanning trips (check previous day + 24h)</li>
 * </ol>
 */
public class RouteDirectionTimeMatcher implements FuzzyTripMatcher {

  private static final Logger LOG = LoggerFactory.getLogger(RouteDirectionTimeMatcher.class);
  private static final int SECONDS_IN_DAY = 24 * 60 * 60;

  private final TransitService transitService;

  public RouteDirectionTimeMatcher(TransitService transitService) {
    this.transitService = Objects.requireNonNull(transitService);
  }

  @Override
  public Result<TripAndPattern, UpdateError> match(
    TripReference tripReference,
    ParsedTripUpdate parsedUpdate,
    LocalDate serviceDate
  ) {
    // Validate required fields
    if (!tripReference.hasRouteId()) {
      LOG.debug("Cannot fuzzy match without route ID");
      return Result.failure(
        new UpdateError(tripReference.tripId(), UpdateError.UpdateErrorType.NO_FUZZY_TRIP_MATCH)
      );
    }

    if (!tripReference.hasStartTime()) {
      LOG.debug("Cannot fuzzy match without start time");
      return Result.failure(
        new UpdateError(tripReference.tripId(), UpdateError.UpdateErrorType.NO_FUZZY_TRIP_MATCH)
      );
    }

    // Look up the route
    Route route = transitService.getRoute(tripReference.routeId());
    if (route == null) {
      LOG.debug("Route not found: {}", tripReference.routeId());
      return Result.failure(
        new UpdateError(tripReference.tripId(), UpdateError.UpdateErrorType.NO_FUZZY_TRIP_MATCH)
      );
    }

    // Parse start time
    int startTime = TimeUtils.time(tripReference.startTime());

    // Determine service date (from reference or parameter)
    LocalDate effectiveDate = tripReference.hasStartDate()
      ? tripReference.startDate()
      : serviceDate;

    // Try to find a matching trip
    TripAndPattern match = findTrip(route, tripReference, startTime, effectiveDate);

    if (match == null) {
      // Check if the trip is carried over from previous day (after midnight)
      LocalDate previousDay = effectiveDate.minusDays(1);
      int adjustedTime = startTime + SECONDS_IN_DAY;
      match = findTrip(route, tripReference, adjustedTime, previousDay);
    }

    if (match == null) {
      LOG.debug(
        "No fuzzy match found for route={}, direction={}, startTime={}, date={}",
        tripReference.routeId(),
        tripReference.direction(),
        tripReference.startTime(),
        effectiveDate
      );
      return Result.failure(
        new UpdateError(tripReference.tripId(), UpdateError.UpdateErrorType.NO_FUZZY_TRIP_MATCH)
      );
    }

    LOG.debug(
      "Fuzzy matched trip {} on pattern {}",
      match.trip().getId(),
      match.tripPattern().getId()
    );
    return Result.success(match);
  }

  private TripAndPattern findTrip(
    Route route,
    TripReference tripReference,
    int startTime,
    LocalDate date
  ) {
    TIntSet servicesRunningForDate = transitService.getServiceCodesRunningForDate(date);

    for (TripPattern pattern : transitService.findPatterns(route)) {
      // Filter by direction if specified
      if (
        tripReference.direction() != null && pattern.getDirection() != tripReference.direction()
      ) {
        continue;
      }

      for (TripTimes times : pattern.getScheduledTimetable().getTripTimes()) {
        if (
          times.getScheduledDepartureTime(0) == startTime &&
          servicesRunningForDate.contains(times.getServiceCode())
        ) {
          return new TripAndPattern(times.getTrip(), pattern);
        }
      }
    }

    return null;
  }
}
