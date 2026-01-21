package org.opentripplanner.updater.trip;

import gnu.trove.set.TIntSet;
import java.time.LocalDate;
import java.util.Objects;
import javax.annotation.Nullable;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.transit.model.framework.Result;
import org.opentripplanner.transit.model.network.Route;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.timetable.Direction;
import org.opentripplanner.transit.model.timetable.Trip;
import org.opentripplanner.transit.model.timetable.TripTimes;
import org.opentripplanner.transit.service.TransitService;
import org.opentripplanner.updater.spi.UpdateError;
import org.opentripplanner.updater.trip.model.ParsedTripUpdate;
import org.opentripplanner.updater.trip.siri.TripAndPattern;
import org.opentripplanner.utils.time.TimeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Trip matcher that implements fuzzy matching for GTFS-RT updates using information from
 * ParsedTripUpdate. Matches trips by route ID, direction, start time, and start date.
 * Based on the legacy GtfsRealtimeFuzzyTripMatcher but adapted to work with the unified
 * ParsedTripUpdate model.
 */
public class GtfsTripMatcher implements TripMatcher {

  private static final Logger LOG = LoggerFactory.getLogger(GtfsTripMatcher.class);

  private final TransitService transitService;
  private final String feedId;

  public GtfsTripMatcher(TransitService transitService, String feedId) {
    this.transitService = Objects.requireNonNull(transitService);
    this.feedId = Objects.requireNonNull(feedId);
  }

  @Override
  public Result<TripAndPattern, UpdateError> match(
    ParsedTripUpdate parsedUpdate,
    TripUpdateApplierContext context
  ) {
    var tripRef = parsedUpdate.tripReference();
    var stopUpdates = parsedUpdate.stopTimeUpdates();

    if (stopUpdates.isEmpty()) {
      return Result.failure(
        new UpdateError(null, UpdateError.UpdateErrorType.NO_VALID_STOPS, feedId)
      );
    }

    if (!tripRef.hasRouteId() || !tripRef.hasStartTime() || tripRef.direction() == null) {
      return Result.failure(
        new UpdateError(null, UpdateError.UpdateErrorType.NO_FUZZY_TRIP_MATCH, feedId)
      );
    }

    FeedScopedId routeId = tripRef.routeId();
    String startTimeStr = tripRef.startTime();
    int startTime = TimeUtils.time(startTimeStr);
    Direction direction = tripRef.direction();
    LocalDate serviceDate = parsedUpdate.serviceDate();

    Route route = transitService.getRoute(routeId);
    if (route == null) {
      LOG.debug("Route not found: {}", routeId);
      return Result.failure(
        new UpdateError(null, UpdateError.UpdateErrorType.NO_FUZZY_TRIP_MATCH, feedId)
      );
    }

    Trip matchedTrip = findTrip(route, direction, startTime, serviceDate);

    if (matchedTrip == null) {
      LocalDate previousDate = serviceDate.minusDays(1);
      int carryoverTime = startTime + 24 * 60 * 60;
      matchedTrip = findTrip(route, direction, carryoverTime, previousDate);

      if (matchedTrip != null) {
        LOG.debug(
          "Matched trip {} with carryover from previous day (service date: {}, time: {})",
          matchedTrip.getId(),
          previousDate,
          TimeUtils.timeToStrCompact(carryoverTime)
        );
      }
    }

    if (matchedTrip == null) {
      LOG.debug(
        "No trip found for route {} direction {} start time {} on date {}",
        routeId,
        direction,
        startTimeStr,
        serviceDate
      );
      return Result.failure(
        new UpdateError(null, UpdateError.UpdateErrorType.NO_FUZZY_TRIP_MATCH, feedId)
      );
    }

    TripPattern pattern = findPatternForTrip(matchedTrip);
    if (pattern == null) {
      LOG.warn("No pattern found for matched trip {}", matchedTrip.getId());
      return Result.failure(
        new UpdateError(null, UpdateError.UpdateErrorType.NO_FUZZY_TRIP_MATCH, feedId)
      );
    }

    LOG.debug(
      "Fuzzy matched trip {} (route {}, direction {}, start time {})",
      matchedTrip.getId(),
      routeId,
      direction,
      startTimeStr
    );

    return Result.success(new TripAndPattern(matchedTrip, pattern));
  }

  @Nullable
  private Trip findTrip(Route route, Direction direction, int startTime, LocalDate date) {
    TIntSet servicesRunningForDate = transitService.getServiceCodesRunningForDate(date);

    for (TripPattern pattern : transitService.findPatterns(route)) {
      if (pattern.getDirection() != direction) {
        continue;
      }

      for (TripTimes times : pattern.getScheduledTimetable().getTripTimes()) {
        if (
          times.getScheduledDepartureTime(0) == startTime &&
          servicesRunningForDate.contains(times.getServiceCode())
        ) {
          return times.getTrip();
        }
      }
    }

    return null;
  }

  @Nullable
  private TripPattern findPatternForTrip(Trip trip) {
    for (TripPattern pattern : transitService.findPatterns(trip.getRoute())) {
      if (pattern.scheduledTripsAsStream().anyMatch(t -> t.equals(trip))) {
        return pattern;
      }
    }
    return null;
  }
}
