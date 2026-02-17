package org.opentripplanner.updater.trip;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.opentripplanner.model.calendar.CalendarService;
import org.opentripplanner.transit.model.basic.TransitMode;
import org.opentripplanner.transit.model.framework.Result;
import org.opentripplanner.transit.model.network.Route;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.site.RegularStop;
import org.opentripplanner.transit.model.site.StopLocation;
import org.opentripplanner.transit.model.timetable.Trip;
import org.opentripplanner.transit.model.timetable.TripTimes;
import org.opentripplanner.transit.service.TransitService;
import org.opentripplanner.updater.spi.UpdateError;
import org.opentripplanner.updater.trip.model.ParsedExistingTripUpdate;
import org.opentripplanner.updater.trip.model.ParsedStopTimeUpdate;
import org.opentripplanner.updater.trip.model.StopReference;
import org.opentripplanner.updater.trip.model.TripReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SIRI-style fuzzy trip matcher that matches trips by last stop arrival time.
 * <p>
 * This matcher is used when exact trip ID matching fails and the real-time feed
 * provides stop information that can be used to identify the trip.
 * <p>
 * Matching algorithm:
 * <ol>
 *   <li>Build cache: (lastStopId, arrivalTimeSeconds) → Set&lt;Trip&gt;</li>
 *   <li>Get aimed arrival time at last stop from ParsedStopTimeUpdate</li>
 *   <li>Look up candidate trips from cache</li>
 *   <li>Filter by route if routeId provided</li>
 *   <li>Match first/last stops (including sibling stops in same station)</li>
 *   <li>Validate departure time at first stop</li>
 *   <li>Validate service date</li>
 * </ol>
 */
public class LastStopArrivalTimeMatcher implements FuzzyTripMatcher {

  private static final Logger LOG = LoggerFactory.getLogger(LastStopArrivalTimeMatcher.class);
  private static final int SECONDS_IN_DAY = 24 * 60 * 60;

  private final TransitService transitService;
  private final StopResolver stopResolver;
  private final ZoneId timeZone;

  // Cache: (stopId:arrivalTime) -> Set<Trip>
  private final Map<String, Set<Trip>> lastStopArrivalCache = new HashMap<>();
  // Cache: internalPlanningCode -> Set<Trip> (for RAIL trips)
  private final Map<String, Set<Trip>> internalPlanningCodeCache = new HashMap<>();
  private boolean cacheInitialized = false;

  public LastStopArrivalTimeMatcher(
    TransitService transitService,
    StopResolver stopResolver,
    ZoneId timeZone
  ) {
    this.transitService = Objects.requireNonNull(transitService);
    this.stopResolver = Objects.requireNonNull(stopResolver);
    this.timeZone = Objects.requireNonNull(timeZone);
  }

  @Override
  public Result<TripAndPattern, UpdateError> match(
    TripReference tripReference,
    ParsedExistingTripUpdate parsedUpdate,
    LocalDate serviceDate
  ) {
    ensureCacheInitialized();

    List<ParsedStopTimeUpdate> stopTimeUpdates = parsedUpdate.stopTimeUpdates();
    if (stopTimeUpdates.isEmpty()) {
      LOG.debug("Cannot fuzzy match without stop time updates");
      return Result.failure(
        new UpdateError(tripReference.tripId(), UpdateError.UpdateErrorType.NO_VALID_STOPS)
      );
    }

    // Get first and last stop updates
    ParsedStopTimeUpdate firstStopUpdate = stopTimeUpdates.getFirst();
    ParsedStopTimeUpdate lastStopUpdate = stopTimeUpdates.getLast();

    // Get the aimed departure time at first stop (for matching)
    Integer aimedDepartureSeconds = getAimedDepartureSeconds(firstStopUpdate, serviceDate);
    if (aimedDepartureSeconds == null) {
      LOG.debug("Cannot fuzzy match without aimed departure time at first stop");
      return Result.failure(
        new UpdateError(tripReference.tripId(), UpdateError.UpdateErrorType.NO_FUZZY_TRIP_MATCH)
      );
    }

    // Get the aimed arrival time at last stop (for cache lookup)
    Integer aimedArrivalSeconds = getAimedArrivalSeconds(lastStopUpdate, serviceDate);
    if (aimedArrivalSeconds == null) {
      // Fall back to departure time if arrival not available
      aimedArrivalSeconds = getAimedDepartureSeconds(lastStopUpdate, serviceDate);
    }
    if (aimedArrivalSeconds == null) {
      LOG.debug("Cannot fuzzy match without aimed arrival time at last stop");
      return Result.failure(
        new UpdateError(tripReference.tripId(), UpdateError.UpdateErrorType.NO_FUZZY_TRIP_MATCH)
      );
    }

    // Resolve first and last stops
    StopLocation firstStop = resolveStop(firstStopUpdate.stopReference());
    StopLocation lastStop = resolveStop(lastStopUpdate.stopReference());
    if (firstStop == null || lastStop == null) {
      LOG.debug("Cannot resolve first or last stop for fuzzy matching");
      return Result.failure(
        new UpdateError(tripReference.tripId(), UpdateError.UpdateErrorType.NO_VALID_STOPS)
      );
    }

    // Try matching by internal planning code first (for RAIL trips with VehicleRef)
    if (tripReference.hasInternalPlanningCode()) {
      Set<Trip> codeCandidates = internalPlanningCodeCache.get(
        tripReference.internalPlanningCode()
      );
      if (codeCandidates != null && !codeCandidates.isEmpty()) {
        codeCandidates = new HashSet<>(codeCandidates);
        if (tripReference.hasRouteId()) {
          Route route = transitService.getRoute(tripReference.routeId());
          if (route != null) {
            codeCandidates = filterByRoute(codeCandidates, route);
          }
        }
        if (!codeCandidates.isEmpty()) {
          var result = findExactMatch(
            codeCandidates,
            firstStop,
            lastStop,
            aimedDepartureSeconds,
            serviceDate,
            tripReference
          );
          if (result.isSuccess()) {
            return result;
          }
        }
      }
    }

    // Look up candidate trips by last stop arrival time
    Set<Trip> candidateTrips = findCandidateTrips(lastStop, aimedArrivalSeconds, serviceDate);
    if (candidateTrips.isEmpty()) {
      LOG.debug(
        "No candidate trips found for last stop {} at time {}",
        lastStop.getId(),
        aimedArrivalSeconds
      );
      return Result.failure(
        new UpdateError(tripReference.tripId(), UpdateError.UpdateErrorType.NO_FUZZY_TRIP_MATCH)
      );
    }

    // Filter by route if provided
    if (tripReference.hasRouteId()) {
      Route route = transitService.getRoute(tripReference.routeId());
      if (route != null) {
        candidateTrips = filterByRoute(candidateTrips, route);
      }
    }

    if (candidateTrips.isEmpty()) {
      LOG.debug("No candidate trips after route filtering");
      return Result.failure(
        new UpdateError(tripReference.tripId(), UpdateError.UpdateErrorType.NO_FUZZY_TRIP_MATCH)
      );
    }

    // Find exact match by first/last stop and departure time
    return findExactMatch(
      candidateTrips,
      firstStop,
      lastStop,
      aimedDepartureSeconds,
      serviceDate,
      tripReference
    );
  }

  private void ensureCacheInitialized() {
    if (cacheInitialized) {
      return;
    }
    synchronized (this) {
      if (cacheInitialized) {
        return;
      }
      initCache();
      cacheInitialized = true;
    }
  }

  private void initCache() {
    for (Trip trip : transitService.listTrips()) {
      TripPattern tripPattern = transitService.findPattern(trip);
      if (tripPattern == null) {
        continue;
      }

      String lastStopId = tripPattern.lastStop().getId().getId();
      TripTimes tripTimes = tripPattern.getScheduledTimetable().getTripTimes(trip);
      if (tripTimes != null) {
        int arrivalTime = tripTimes.getArrivalTime(tripTimes.getNumStops() - 1);
        String key = createCacheKey(lastStopId, arrivalTime);
        lastStopArrivalCache.computeIfAbsent(key, k -> new HashSet<>()).add(trip);
      }

      if (tripPattern.getRoute().getMode().equals(TransitMode.RAIL)) {
        String planningCode = trip.getNetexInternalPlanningCode();
        if (planningCode != null) {
          internalPlanningCodeCache.computeIfAbsent(planningCode, k -> new HashSet<>()).add(trip);
        }
      }
    }
    LOG.info(
      "Built last-stop-arrival cache with {} entries, planning code cache with {} entries",
      lastStopArrivalCache.size(),
      internalPlanningCodeCache.size()
    );
  }

  private static String createCacheKey(String stopId, int arrivalTimeSeconds) {
    return stopId + ":" + arrivalTimeSeconds;
  }

  private static String createCacheKey(StopLocation stop, int arrivalTimeSeconds) {
    return createCacheKey(stop.getId().getId(), arrivalTimeSeconds);
  }

  private Integer getAimedDepartureSeconds(ParsedStopTimeUpdate stopUpdate, LocalDate serviceDate) {
    return stopUpdate.resolveScheduledDepartureSeconds(serviceDate, timeZone);
  }

  private Integer getAimedArrivalSeconds(ParsedStopTimeUpdate stopUpdate, LocalDate serviceDate) {
    return stopUpdate.resolveScheduledArrivalSeconds(serviceDate, timeZone);
  }

  private StopLocation resolveStop(StopReference stopReference) {
    return stopResolver.resolve(stopReference);
  }

  private Set<Trip> findCandidateTrips(
    StopLocation lastStop,
    int aimedArrivalSeconds,
    LocalDate serviceDate
  ) {
    Set<Trip> trips = new HashSet<>();

    // Try exact match
    String key = createCacheKey(lastStop, aimedArrivalSeconds);
    Set<Trip> exactMatches = lastStopArrivalCache.get(key);
    if (exactMatches != null) {
      trips.addAll(exactMatches);
    }

    // Try yesterday (for trips that span midnight)
    int yesterdayArrival = aimedArrivalSeconds + SECONDS_IN_DAY;
    String yesterdayKey = createCacheKey(lastStop, yesterdayArrival);
    Set<Trip> yesterdayMatches = lastStopArrivalCache.get(yesterdayKey);
    if (yesterdayMatches != null) {
      trips.addAll(yesterdayMatches);
    }

    // Try sibling stops (same parent station)
    if (lastStop instanceof RegularStop regularStop && regularStop.isPartOfStation()) {
      var allQuays = regularStop.getParentStation().getChildStops();
      for (var quay : allQuays) {
        // Skip the stop we already checked
        if (quay.equals(lastStop)) {
          continue;
        }
        String siblingKey = createCacheKey(quay, aimedArrivalSeconds);
        Set<Trip> siblingMatches = lastStopArrivalCache.get(siblingKey);
        if (siblingMatches != null) {
          trips.addAll(siblingMatches);
        }
      }
    }

    return trips;
  }

  private Set<Trip> filterByRoute(Set<Trip> trips, Route route) {
    Set<Trip> filtered = new HashSet<>();
    for (Trip trip : trips) {
      if (trip.getRoute().equals(route)) {
        filtered.add(trip);
      }
    }
    return filtered;
  }

  private Result<TripAndPattern, UpdateError> findExactMatch(
    Set<Trip> candidateTrips,
    StopLocation journeyFirstStop,
    StopLocation journeyLastStop,
    int aimedDepartureSeconds,
    LocalDate serviceDate,
    TripReference tripReference
  ) {
    CalendarService calendarService = transitService.getCalendarService();
    Set<TripAndPattern> matches = new HashSet<>();

    for (Trip trip : candidateTrips) {
      // Check service date
      Set<LocalDate> serviceDates = calendarService.getServiceDatesForServiceId(
        trip.getServiceId()
      );
      if (!serviceDates.contains(serviceDate)) {
        continue;
      }

      TripPattern pattern = transitService.findPattern(trip);
      if (pattern == null) {
        continue;
      }

      // Check first/last stop match (including sibling stops)
      StopLocation patternFirstStop = pattern.firstStop();
      StopLocation patternLastStop = pattern.lastStop();

      boolean firstStopMatches = stopsMatch(patternFirstStop, journeyFirstStop);
      boolean lastStopMatches = stopsMatch(patternLastStop, journeyLastStop);

      if (!firstStopMatches || !lastStopMatches) {
        continue;
      }

      // Check departure time at first stop
      TripTimes times = pattern.getScheduledTimetable().getTripTimes(trip);
      if (times != null && times.getScheduledDepartureTime(0) == aimedDepartureSeconds) {
        matches.add(new TripAndPattern(trip, pattern));
      }
    }

    if (matches.isEmpty()) {
      return Result.failure(
        new UpdateError(tripReference.tripId(), UpdateError.UpdateErrorType.NO_FUZZY_TRIP_MATCH)
      );
    }

    if (matches.size() > 1) {
      LOG.warn("Multiple fuzzy matches found ({}), skipping all: {}", matches.size(), matches);
      return Result.failure(
        new UpdateError(
          tripReference.tripId(),
          UpdateError.UpdateErrorType.MULTIPLE_FUZZY_TRIP_MATCHES
        )
      );
    }

    TripAndPattern match = matches.iterator().next();
    LOG.debug(
      "Fuzzy matched trip {} on pattern {}",
      match.trip().getId(),
      match.tripPattern().getId()
    );
    return Result.success(match);
  }

  private boolean stopsMatch(StopLocation patternStop, StopLocation journeyStop) {
    // Direct ID match
    if (patternStop.getId().equals(journeyStop.getId())) {
      return true;
    }

    // Check if both are part of the same parent station
    if (patternStop instanceof RegularStop ps && journeyStop instanceof RegularStop js) {
      if (ps.isPartOfStation() && js.isPartOfStation()) {
        return ps.getParentStation().getId().equals(js.getParentStation().getId());
      }
    }

    return false;
  }
}
