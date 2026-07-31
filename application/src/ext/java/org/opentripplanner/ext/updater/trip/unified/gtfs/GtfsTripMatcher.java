package org.opentripplanner.ext.updater.trip.unified.gtfs;

import gnu.trove.set.TIntSet;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import org.opentripplanner.ext.updater.trip.unified.model.ServiceTime;
import org.opentripplanner.ext.updater.trip.unified.model.command.TripReference;
import org.opentripplanner.ext.updater.trip.unified.model.command.TripUpdateCommand;
import org.opentripplanner.ext.updater.trip.unified.resolver.FuzzyTripMatcher;
import org.opentripplanner.ext.updater.trip.unified.resolver.TripAndPattern;
import org.opentripplanner.transit.model.network.Route;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.timetable.TripTimes;
import org.opentripplanner.transit.service.TransitService;
import org.opentripplanner.updater.spi.UpdateErrorType;
import org.opentripplanner.updater.spi.UpdateException;
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
 * A failed match is a non-answer, never a verdict: the legacy matcher hands the descriptor back
 * unchanged ({@code GtfsRealtimeFuzzyTripMatcher}) and the message is then judged exactly as it
 * arrived. One carrying an unknown trip id is rejected for that trip being unknown - what the
 * caller was about to report anyway - so this matcher declines with {@code Optional.empty()} and
 * never manufactures an error of its own for it. One carrying no trip id at all has, at this
 * point, identified its trip by nothing whatsoever, and that verdict is this matcher's to give:
 * structurally invalid, the same answer legacy's post-match validation gives it.
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
  public Optional<TripAndPattern> match(
    TripReference tripReference,
    TripUpdateCommand command,
    LocalDate serviceDate
  ) {
    // Validate required fields
    if (!tripReference.hasRouteId()) {
      LOG.debug("Cannot fuzzy match without route ID");
      return declineOrReject(tripReference);
    }

    if (!tripReference.hasStartTime()) {
      LOG.debug("Cannot fuzzy match without start time");
      return declineOrReject(tripReference);
    }

    if (!tripReference.hasDirection()) {
      LOG.debug("Cannot fuzzy match without direction");
      return declineOrReject(tripReference);
    }

    if (!tripReference.hasStartDate()) {
      LOG.debug("Cannot fuzzy match without start date");
      return declineOrReject(tripReference);
    }

    // Look up the route
    Route route = transitService.getRoute(tripReference.routeId());
    if (route == null) {
      LOG.debug("Route not found: {}", tripReference.routeId());
      return declineOrReject(tripReference);
    }

    ServiceTime startTime = tripReference.startTime();

    // The date the feed reported, never the one resolved for applying the update: that one falls back
    // on the current date, which would match whichever trip runs today.
    LocalDate reportedDate = tripReference.startDate();

    // Try to find a matching trip
    TripAndPattern match = findTrip(route, tripReference, startTime, reportedDate);

    if (match == null) {
      // Check if the trip is carried over from the previous day: registered on that service date,
      // it is timed one service day later in its own numbering (08:00 today is its 32:00).
      LocalDate previousDay = reportedDate.minusDays(1);
      match = findTrip(route, tripReference, startTime.plusDays(1), previousDay);
    }

    if (match == null) {
      LOG.debug(
        "No fuzzy match found for route={}, direction={}, startTime={}, date={}",
        tripReference.routeId(),
        tripReference.direction(),
        tripReference.startTime(),
        reportedDate
      );
      return declineOrReject(tripReference);
    }

    LOG.debug(
      "Fuzzy matched trip {} on pattern {}",
      match.trip().getId(),
      match.tripPattern().getId()
    );
    return Optional.of(match);
  }

  /**
   * No verdict for a message that named a trip, an own verdict for one that named none. A message
   * carrying a trip id is rejected by the caller for that id being unknown, exactly as if no
   * matcher had run. One carrying no trip id has identified its trip by nothing at all once the
   * match fails, and no caller can know that - only the matcher saw whether the tuple named a
   * trip - so the structurally-invalid verdict legacy reaches through its post-match validation
   * ({@code TripUpdate.validate()}) is produced here.
   */
  private Optional<TripAndPattern> declineOrReject(TripReference tripReference) {
    if (!tripReference.hasTripId()) {
      throw UpdateException.noTripId(UpdateErrorType.INVALID_INPUT_STRUCTURE);
    }
    return Optional.empty();
  }

  private TripAndPattern findTrip(
    Route route,
    TripReference tripReference,
    ServiceTime startTime,
    LocalDate date
  ) {
    TIntSet servicesRunningForDate = transitService.getServiceCodesRunningForDate(date);
    int departureSeconds = startTime.secondsPastMidnight();

    for (TripPattern pattern : transitService.findPatterns(route)) {
      if (pattern.getDirection() != tripReference.direction()) {
        continue;
      }

      for (TripTimes times : pattern.getScheduledTimetable().getTripTimes()) {
        if (
          times.getScheduledDepartureTime(0) == departureSeconds &&
          servicesRunningForDate.contains(times.getServiceCode())
        ) {
          return new TripAndPattern(times.getTrip(), pattern);
        }
      }
    }

    return null;
  }
}
