package org.opentripplanner.ext.updater.trip.unified.gtfs;

import gnu.trove.set.TIntSet;
import java.time.LocalDate;
import java.util.Objects;
import org.opentripplanner.ext.updater.trip.unified.model.command.ExistingTripCommand;
import org.opentripplanner.ext.updater.trip.unified.model.command.TripReference;
import org.opentripplanner.ext.updater.trip.unified.resolver.FuzzyTripMatcher;
import org.opentripplanner.ext.updater.trip.unified.resolver.TripAndPattern;
import org.opentripplanner.transit.model.network.Route;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.timetable.TripTimes;
import org.opentripplanner.transit.service.TransitService;
import org.opentripplanner.updater.spi.UpdateErrorType;
import org.opentripplanner.updater.spi.UpdateException;
import org.opentripplanner.utils.time.TimeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * GTFS-RT style fuzzy trip matcher that matches trips by route, direction, and start time.
 * <p>
 * This matcher is used when the real-time feed provides route/direction/start time
 * but not a direct trip ID. It searches for a matching trip in the schedule.
 * <p>
 * Route, direction, start time and start date are all required: together they identify one trip, and
 * no subset of them does. The search compares the departure time at the first stop without looking at
 * which stop that is, so a line worked from both ends at once - the outbound and the inbound trip
 * both leaving at 08:00 - offers two equally good candidates. Matching one of them at random would
 * write the update onto a trip running the other way, which is worse than reporting no match at all.
 * The date has to be one the feed reported for the same reason: a date guessed on its behalf would
 * pick out whichever trip runs today.
 * <p>
 * Matching algorithm:
 * <ol>
 *   <li>Look up route by ID from {@link TripReference#routeId()}</li>
 *   <li>Get all patterns for the route</li>
 *   <li>Keep only the patterns going in the direction from {@link TripReference#direction()}</li>
 *   <li>Find trip with matching departure time at first stop</li>
 *   <li>Validate service runs on {@link TripReference#startDate()}</li>
 *   <li>Handle midnight-spanning trips (check previous day + 24h)</li>
 * </ol>
 */
public class GtfsTripMatcher implements FuzzyTripMatcher {

  private static final Logger LOG = LoggerFactory.getLogger(GtfsTripMatcher.class);
  private static final int SECONDS_IN_DAY = 24 * 60 * 60;

  private final TransitService transitService;

  public GtfsTripMatcher(TransitService transitService) {
    this.transitService = Objects.requireNonNull(transitService);
  }

  /**
   * @param serviceDate the date the update will be applied on, which this matcher deliberately does
   *                    not match against: for GTFS-RT it is the reported date or, failing that, today,
   *                    and only a reported date identifies a trip.
   */
  @Override
  public TripAndPattern match(
    TripReference tripReference,
    ExistingTripCommand command,
    LocalDate serviceDate
  ) {
    // Validate required fields
    if (!tripReference.hasRouteId()) {
      LOG.debug("Cannot fuzzy match without route ID");
      throw UpdateException.of(tripReference.tripId(), UpdateErrorType.NO_FUZZY_TRIP_MATCH);
    }

    if (!tripReference.hasStartTime()) {
      LOG.debug("Cannot fuzzy match without start time");
      throw UpdateException.of(tripReference.tripId(), UpdateErrorType.NO_FUZZY_TRIP_MATCH);
    }

    if (!tripReference.hasDirection()) {
      LOG.debug("Cannot fuzzy match without direction");
      throw UpdateException.of(tripReference.tripId(), UpdateErrorType.NO_FUZZY_TRIP_MATCH);
    }

    if (!tripReference.hasStartDate()) {
      LOG.debug("Cannot fuzzy match without start date");
      throw UpdateException.of(tripReference.tripId(), UpdateErrorType.NO_FUZZY_TRIP_MATCH);
    }

    // Look up the route
    Route route = transitService.getRoute(tripReference.routeId());
    if (route == null) {
      LOG.debug("Route not found: {}", tripReference.routeId());
      throw UpdateException.of(tripReference.tripId(), UpdateErrorType.NO_FUZZY_TRIP_MATCH);
    }

    // Parse start time
    int startTime = TimeUtils.time(tripReference.startTime());

    // The date the feed reported, never the one resolved for applying the update: that one falls back
    // on the current date, which would match whichever trip runs today.
    LocalDate reportedDate = tripReference.startDate();

    // Try to find a matching trip
    TripAndPattern match = findTrip(route, tripReference, startTime, reportedDate);

    if (match == null) {
      // Check if the trip is carried over from previous day (after midnight)
      LocalDate previousDay = reportedDate.minusDays(1);
      int adjustedTime = startTime + SECONDS_IN_DAY;
      match = findTrip(route, tripReference, adjustedTime, previousDay);
    }

    if (match == null) {
      LOG.debug(
        "No fuzzy match found for route={}, direction={}, startTime={}, date={}",
        tripReference.routeId(),
        tripReference.direction(),
        tripReference.startTime(),
        reportedDate
      );
      throw UpdateException.of(tripReference.tripId(), UpdateErrorType.NO_FUZZY_TRIP_MATCH);
    }

    LOG.debug(
      "Fuzzy matched trip {} on pattern {}",
      match.trip().getId(),
      match.tripPattern().getId()
    );
    return match;
  }

  private TripAndPattern findTrip(
    Route route,
    TripReference tripReference,
    int startTime,
    LocalDate date
  ) {
    TIntSet servicesRunningForDate = transitService.getServiceCodesRunningForDate(date);

    for (TripPattern pattern : transitService.findPatterns(route)) {
      if (pattern.getDirection() != tripReference.direction()) {
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
