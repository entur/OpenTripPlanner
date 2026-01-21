package org.opentripplanner.updater.trip;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.transit.model.basic.TransitMode;
import org.opentripplanner.transit.model.framework.Result;
import org.opentripplanner.transit.model.network.Route;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.site.RegularStop;
import org.opentripplanner.transit.model.timetable.Trip;
import org.opentripplanner.transit.model.timetable.TripTimes;
import org.opentripplanner.transit.service.TransitService;
import org.opentripplanner.updater.spi.UpdateError;
import org.opentripplanner.updater.trip.model.ParsedStopTimeUpdate;
import org.opentripplanner.updater.trip.model.ParsedTripUpdate;
import org.opentripplanner.updater.trip.siri.TripAndPattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Trip matcher that implements fuzzy matching for SIRI-based updates using information from
 * ParsedTripUpdate. Matches trips by:
 * 1. Vehicle ref (internal planning code for rail)
 * 2. Last stop + arrival time (fallback)
 * 3. Filtered by line ref if available
 * 4. Validated against first/last stops and departure time
 */
public class SiriTripMatcher implements TripMatcher {

  private static final Logger LOG = LoggerFactory.getLogger(SiriTripMatcher.class);

  private final Map<String, Set<Trip>> internalPlanningCodeCache = new HashMap<>();
  private final Map<String, Set<Trip>> lastStopArrivalCache = new HashMap<>();
  private final TransitService transitService;
  private final String feedId;

  public SiriTripMatcher(TransitService transitService, String feedId) {
    this.transitService = Objects.requireNonNull(transitService);
    this.feedId = Objects.requireNonNull(feedId);
    initCache();
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

    // Get first stop with departure time
    ParsedStopTimeUpdate firstStop = stopUpdates.get(0);
    if (firstStop.departureUpdate() == null || !firstStop.departureUpdate().hasAbsoluteTime()) {
      return Result.failure(
        new UpdateError(null, UpdateError.UpdateErrorType.NO_FUZZY_TRIP_MATCH, feedId)
      );
    }

    int firstDepartureSeconds = firstStop.departureUpdate().absoluteTimeSecondsSinceMidnight();
    LocalDate serviceDate = parsedUpdate.serviceDate();

    // Step 1: Get candidate trips by vehicle ref (if available) or last stop
    Set<Trip> candidateTrips = getCandidateTrips(tripRef, stopUpdates, serviceDate);

    if (candidateTrips.isEmpty()) {
      return Result.failure(
        new UpdateError(null, UpdateError.UpdateErrorType.NO_FUZZY_TRIP_MATCH, feedId)
      );
    }

    // Step 2: Filter by line ref if available
    if (tripRef.lineRef() != null) {
      Route route = transitService.getRoute(new FeedScopedId(feedId, tripRef.lineRef()));
      if (route != null) {
        candidateTrips = candidateTrips
          .stream()
          .filter(trip -> trip.getRoute().equals(route))
          .collect(Collectors.toSet());
      }
    }

    if (candidateTrips.isEmpty()) {
      return Result.failure(
        new UpdateError(null, UpdateError.UpdateErrorType.NO_FUZZY_TRIP_MATCH, feedId)
      );
    }

    // Step 3: Match by first/last stops and departure time
    return matchByStopsAndTime(
      candidateTrips,
      stopUpdates,
      firstDepartureSeconds,
      serviceDate,
      context
    );
  }

  private void initCache() {
    for (Trip trip : transitService.listTrips()) {
      TripPattern tripPattern = transitService.findPattern(trip);

      if (tripPattern == null) {
        continue;
      }

      // Cache internal planning codes for rail trips
      if (tripPattern.getRoute().getMode().equals(TransitMode.RAIL)) {
        String internalPlanningCode = trip.getNetexInternalPlanningCode();
        if (internalPlanningCode != null) {
          internalPlanningCodeCache
            .computeIfAbsent(internalPlanningCode, key -> new HashSet<>())
            .add(trip);
        }
      }

      // Cache last stop arrival times
      String lastStopId = tripPattern.lastStop().getId().getId();
      TripTimes tripTimes = tripPattern.getScheduledTimetable().getTripTimes(trip);
      if (tripTimes != null) {
        int arrivalTime = tripTimes.getArrivalTime(tripTimes.getNumStops() - 1);
        String key = createLastStopKey(lastStopId, arrivalTime);
        lastStopArrivalCache.computeIfAbsent(key, k -> new HashSet<>()).add(trip);
      }
    }

    LOG.info("Built internal planning code cache [{}].", internalPlanningCodeCache.size());
    LOG.info("Built last stop arrival cache [{}].", lastStopArrivalCache.size());
  }

  private static String createLastStopKey(String lastStopId, int lastStopArrivalTime) {
    return lastStopId + ":" + lastStopArrivalTime;
  }

  private Set<Trip> getCandidateTrips(
    org.opentripplanner.updater.trip.model.TripReference tripRef,
    List<ParsedStopTimeUpdate> stopUpdates,
    LocalDate serviceDate
  ) {
    // Try vehicle ref first (for rail - uses internal planning code)
    if (tripRef.vehicleRef() != null) {
      Set<Trip> cachedTrips = internalPlanningCodeCache.get(tripRef.vehicleRef());
      if (cachedTrips != null) {
        return cachedTrips
          .stream()
          .filter(trip ->
            transitService
              .getCalendarService()
              .getServiceDatesForServiceId(trip.getServiceId())
              .contains(serviceDate)
          )
          .collect(Collectors.toSet());
      }
    }

    // Fallback: match by last stop + arrival time
    ParsedStopTimeUpdate lastStop = stopUpdates.get(stopUpdates.size() - 1);
    if (lastStop.arrivalUpdate() != null && lastStop.arrivalUpdate().hasAbsoluteTime()) {
      FeedScopedId lastStopId = lastStop.stopReference().stopId();
      RegularStop stop = (RegularStop) transitService.getRegularStop(lastStopId);
      if (stop != null) {
        int arrivalSeconds = lastStop.arrivalUpdate().absoluteTimeSecondsSinceMidnight();
        return getMatchingTripsOnStopOrSiblings(stop, arrivalSeconds);
      }
    }

    return Set.of();
  }

  private Set<Trip> getMatchingTripsOnStopOrSiblings(RegularStop lastStop, int arrivalSeconds) {
    Set<Trip> trips = lastStopArrivalCache.get(
      createLastStopKey(lastStop.getId().getId(), arrivalSeconds)
    );

    if (trips != null) {
      return trips;
    }

    // SIRI data may report a different platform but still on the same parent stop
    if (!lastStop.isPartOfStation()) {
      return Set.of();
    }

    trips = new HashSet<>();
    var allQuays = lastStop.getParentStation().getChildStops();
    for (var quay : allQuays) {
      Set<Trip> tripSet = lastStopArrivalCache.get(
        createLastStopKey(quay.getId().getId(), arrivalSeconds)
      );
      if (tripSet != null) {
        trips.addAll(tripSet);
      }
    }
    return trips;
  }

  private Result<TripAndPattern, UpdateError> matchByStopsAndTime(
    Set<Trip> candidateTrips,
    List<ParsedStopTimeUpdate> stopUpdates,
    int firstDepartureSeconds,
    LocalDate serviceDate,
    TripUpdateApplierContext context
  ) {
    ParsedStopTimeUpdate firstStop = stopUpdates.get(0);
    ParsedStopTimeUpdate lastStop = stopUpdates.get(stopUpdates.size() - 1);

    FeedScopedId firstStopId = firstStop.stopReference().stopId();
    FeedScopedId lastStopId = lastStop.stopReference().stopId();

    RegularStop journeyFirstStop = (RegularStop) transitService.getRegularStop(firstStopId);
    RegularStop journeyLastStop = (RegularStop) transitService.getRegularStop(lastStopId);

    if (journeyFirstStop == null || journeyLastStop == null) {
      return Result.failure(
        new UpdateError(null, UpdateError.UpdateErrorType.NO_VALID_STOPS, feedId)
      );
    }

    Set<TripAndPattern> possibleTrips = new HashSet<>();
    var calendarService = transitService.getCalendarService();

    for (Trip trip : candidateTrips) {
      if (!calendarService.getServiceDatesForServiceId(trip.getServiceId()).contains(serviceDate)) {
        continue;
      }

      TripPattern tripPattern = getPattern(trip, serviceDate, context);
      if (tripPattern == null) {
        continue;
      }

      var firstPatternStop = tripPattern.firstStop();
      var lastPatternStop = tripPattern.lastStop();

      boolean firstStopMatches =
        firstPatternStop.equals(journeyFirstStop) ||
        firstPatternStop.isPartOfSameStationAs(journeyFirstStop);
      boolean lastStopMatches =
        lastPatternStop.equals(journeyLastStop) ||
        lastPatternStop.isPartOfSameStationAs(journeyLastStop);

      if (!firstStopMatches || !lastStopMatches) {
        continue;
      }

      var timetable = getTimetable(tripPattern, serviceDate, context);
      TripTimes times = timetable.getTripTimes(trip);
      if (times != null && times.getScheduledDepartureTime(0) == firstDepartureSeconds) {
        possibleTrips.add(new TripAndPattern(times.getTrip(), tripPattern));
      }
    }

    if (possibleTrips.isEmpty()) {
      return Result.failure(
        new UpdateError(null, UpdateError.UpdateErrorType.NO_FUZZY_TRIP_MATCH, feedId)
      );
    } else if (possibleTrips.size() > 1) {
      LOG.warn("Multiple trip and pattern combinations found, skipping all: {}", possibleTrips);
      return Result.failure(
        new UpdateError(null, UpdateError.UpdateErrorType.MULTIPLE_FUZZY_TRIP_MATCHES, feedId)
      );
    } else {
      return Result.success(possibleTrips.iterator().next());
    }
  }

  private TripPattern getPattern(
    Trip trip,
    LocalDate serviceDate,
    TripUpdateApplierContext context
  ) {
    // Check for modified pattern first
    if (context.snapshotManager() != null) {
      var modifiedPattern = context
        .snapshotManager()
        .getNewTripPatternForModifiedTrip(trip.getId(), serviceDate);
      if (modifiedPattern != null) {
        return modifiedPattern;
      }
    }
    return transitService.findPattern(trip);
  }

  private org.opentripplanner.transit.model.timetable.Timetable getTimetable(
    TripPattern pattern,
    LocalDate serviceDate,
    TripUpdateApplierContext context
  ) {
    if (context.snapshotManager() != null) {
      return context.snapshotManager().resolve(pattern, serviceDate);
    }
    return pattern.getScheduledTimetable();
  }
}
