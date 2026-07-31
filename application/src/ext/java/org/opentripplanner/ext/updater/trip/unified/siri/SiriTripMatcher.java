package org.opentripplanner.ext.updater.trip.unified.siri;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.opentripplanner.ext.updater.trip.unified.model.ServiceTime;
import org.opentripplanner.ext.updater.trip.unified.model.command.AddTrip;
import org.opentripplanner.ext.updater.trip.unified.model.command.DuplicateTrip;
import org.opentripplanner.ext.updater.trip.unified.model.command.ExistingTripCommand;
import org.opentripplanner.ext.updater.trip.unified.model.command.ParsedStopTimeUpdate;
import org.opentripplanner.ext.updater.trip.unified.model.command.RemoveTripCommand;
import org.opentripplanner.ext.updater.trip.unified.model.command.StopReference;
import org.opentripplanner.ext.updater.trip.unified.model.command.TripReference;
import org.opentripplanner.ext.updater.trip.unified.model.command.TripUpdateCommand;
import org.opentripplanner.ext.updater.trip.unified.resolver.FuzzyTripMatcher;
import org.opentripplanner.ext.updater.trip.unified.resolver.StopResolver;
import org.opentripplanner.ext.updater.trip.unified.resolver.TripAndPattern;
import org.opentripplanner.transit.model.network.Route;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.site.RegularStop;
import org.opentripplanner.transit.model.site.StopLocation;
import org.opentripplanner.transit.model.timetable.Trip;
import org.opentripplanner.transit.model.timetable.TripTimes;
import org.opentripplanner.transit.service.TransitService;
import org.opentripplanner.updater.spi.UpdateErrorType;
import org.opentripplanner.updater.spi.UpdateException;
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
public class SiriTripMatcher implements FuzzyTripMatcher {

  private static final Logger LOG = LoggerFactory.getLogger(SiriTripMatcher.class);

  private final SiriTripMatcherCache cache;
  private final TransitService transitService;
  private final StopResolver stopResolver;
  private final ZoneId timeZone;

  public SiriTripMatcher(
    SiriTripMatcherCache cache,
    TransitService transitService,
    StopResolver stopResolver,
    ZoneId timeZone
  ) {
    this.cache = Objects.requireNonNull(cache);
    this.transitService = Objects.requireNonNull(transitService);
    this.stopResolver = Objects.requireNonNull(stopResolver);
    this.timeZone = Objects.requireNonNull(timeZone);
  }

  @Override
  public Optional<TripAndPattern> match(
    TripReference tripReference,
    TripUpdateCommand command,
    LocalDate serviceDate
  ) {
    // A SIRI-ET journey is identified by the stops and times of its calls, so only a command that
    // carries calls can be matched; one that carries none is declined, there being nothing to
    // match on and so nothing to have a verdict about. Legacy SIRI can fuzzy-match a cancellation,
    // because the cancellation message still carries its calls - the unified CancelTrip drops
    // them, a known modelling gap of RemoveTripCommand. If that gap is closed, extending this
    // switch is a deliberate SIRI behaviour change and needs its own tests.
    return switch (command) {
      case ExistingTripCommand existingTripCommand -> Optional.of(
        matchByCalls(tripReference, existingTripCommand, serviceDate)
      );
      case RemoveTripCommand _ -> Optional.empty();
      case AddTrip _ -> Optional.empty();
      case DuplicateTrip _ -> Optional.empty();
    };
  }

  private TripAndPattern matchByCalls(
    TripReference tripReference,
    ExistingTripCommand command,
    LocalDate serviceDate
  ) {
    List<ParsedStopTimeUpdate> stopTimeUpdates = command.stopTimeUpdates();
    if (stopTimeUpdates.isEmpty()) {
      LOG.debug("Cannot fuzzy match without stop time updates");
      throw UpdateException.of(tripReference.tripId(), UpdateErrorType.NO_VALID_STOPS);
    }

    // Get first and last stop updates
    ParsedStopTimeUpdate firstStopUpdate = stopTimeUpdates.getFirst();
    ParsedStopTimeUpdate lastStopUpdate = stopTimeUpdates.getLast();

    // Get the aimed departure time at first stop (for matching)
    ServiceTime aimedDepartureTime = aimedDepartureTime(firstStopUpdate, serviceDate);
    if (aimedDepartureTime == null) {
      LOG.debug("Cannot fuzzy match without aimed departure time at first stop");
      throw UpdateException.of(tripReference.tripId(), UpdateErrorType.INVALID_DEPARTURE_TIME);
    }

    // Get the aimed arrival time at last stop (for cache lookup)
    ServiceTime aimedArrivalTime = aimedArrivalTime(lastStopUpdate, serviceDate);
    if (aimedArrivalTime == null) {
      // Fall back to departure time if arrival not available
      aimedArrivalTime = aimedDepartureTime(lastStopUpdate, serviceDate);
    }
    if (aimedArrivalTime == null) {
      LOG.debug("Cannot fuzzy match without aimed arrival time at last stop");
      throw UpdateException.of(tripReference.tripId(), UpdateErrorType.NO_FUZZY_TRIP_MATCH);
    }

    // Resolve first and last stops
    StopLocation firstStop = resolveStop(firstStopUpdate.stopReference());
    StopLocation lastStop = resolveStop(lastStopUpdate.stopReference());
    if (firstStop == null || lastStop == null) {
      LOG.debug("Cannot resolve first or last stop for fuzzy matching");
      throw UpdateException.of(tripReference.tripId(), UpdateErrorType.NO_VALID_STOPS);
    }

    // Try matching by internal planning code first (for RAIL trips with VehicleRef)
    if (tripReference.hasInternalPlanningCode()) {
      Set<Trip> codeCandidates = cache.tripsByInternalPlanningCode(
        tripReference.internalPlanningCode()
      );
      if (!codeCandidates.isEmpty()) {
        codeCandidates = new HashSet<>(codeCandidates);
        if (tripReference.hasRouteId()) {
          Route route = transitService.getRoute(tripReference.routeId());
          if (route != null) {
            codeCandidates = filterByRoute(codeCandidates, route);
          }
        }
        if (!codeCandidates.isEmpty()) {
          try {
            return findExactMatch(
              codeCandidates,
              firstStop,
              lastStop,
              aimedDepartureTime,
              serviceDate,
              tripReference
            );
          } catch (UpdateException e) {
            // Internal planning code match failed, fall through to arrival time matching
          }
        }
      }
    }

    // Look up candidate trips by last stop arrival time
    Set<Trip> candidateTrips = findCandidateTrips(lastStop, aimedArrivalTime);
    if (candidateTrips.isEmpty()) {
      LOG.debug(
        "No candidate trips found for last stop {} at time {}",
        lastStop.getId(),
        aimedArrivalTime
      );
      throw UpdateException.of(tripReference.tripId(), UpdateErrorType.NO_FUZZY_TRIP_MATCH);
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
      throw UpdateException.of(tripReference.tripId(), UpdateErrorType.NO_FUZZY_TRIP_MATCH);
    }

    // Find exact match by first/last stop and departure time
    return findExactMatch(
      candidateTrips,
      firstStop,
      lastStop,
      aimedDepartureTime,
      serviceDate,
      tripReference
    );
  }

  private ServiceTime aimedDepartureTime(ParsedStopTimeUpdate stopUpdate, LocalDate serviceDate) {
    return stopUpdate.resolveScheduledDepartureTime(serviceDate, timeZone);
  }

  private ServiceTime aimedArrivalTime(ParsedStopTimeUpdate stopUpdate, LocalDate serviceDate) {
    return stopUpdate.resolveScheduledArrivalTime(serviceDate, timeZone);
  }

  private StopLocation resolveStop(StopReference stopReference) {
    return stopResolver.resolveReferencedStop(stopReference);
  }

  private Set<Trip> findCandidateTrips(StopLocation lastStop, ServiceTime aimedArrivalTime) {
    Set<Trip> trips = new HashSet<>();

    // Try exact match
    trips.addAll(cache.tripsByLastStopArrival(lastStop, aimedArrivalTime.secondsPastMidnight()));

    // Try yesterday (for trips that span midnight): a trip timed past 24:00:00 on the previous
    // service date arrives at this wall-clock time one service day later in its own numbering.
    trips.addAll(
      cache.tripsByLastStopArrival(lastStop, aimedArrivalTime.plusDays(1).secondsPastMidnight())
    );

    // Try sibling stops (same parent station)
    if (lastStop instanceof RegularStop regularStop && regularStop.isPartOfStation()) {
      var allQuays = regularStop.getParentStation().getChildStops();
      for (var quay : allQuays) {
        // Skip the stop we already checked
        if (quay.equals(lastStop)) {
          continue;
        }
        trips.addAll(cache.tripsByLastStopArrival(quay, aimedArrivalTime.secondsPastMidnight()));
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

  private TripAndPattern findExactMatch(
    Set<Trip> candidateTrips,
    StopLocation journeyFirstStop,
    StopLocation journeyLastStop,
    ServiceTime aimedDepartureTime,
    LocalDate serviceDate,
    TripReference tripReference
  ) {
    var tripCalendars = transitService.getTripCalendars();
    Set<TripAndPattern> matches = new HashSet<>();

    for (Trip trip : candidateTrips) {
      // Check service date
      Set<LocalDate> serviceDates = tripCalendars.listServiceDates(trip.getServiceId());
      if (!serviceDates.contains(serviceDate)) {
        continue;
      }

      TripPattern scheduledPattern = transitService.findPattern(trip);
      if (scheduledPattern == null) {
        continue;
      }

      // Check first/last stop match (including sibling stops)
      StopLocation patternFirstStop = scheduledPattern.firstStop();
      StopLocation patternLastStop = scheduledPattern.lastStop();

      boolean firstStopMatches = stopsMatch(patternFirstStop, journeyFirstStop);
      boolean lastStopMatches = stopsMatch(patternLastStop, journeyLastStop);

      if (!firstStopMatches || !lastStopMatches) {
        continue;
      }

      // Check departure time at first stop (always use scheduled timetable for matching)
      TripTimes times = scheduledPattern.getScheduledTimetable().getTripTimes(trip);
      if (
        times != null &&
        times.getScheduledDepartureTime(0) == aimedDepartureTime.secondsPastMidnight()
      ) {
        // Return the RT modified pattern if one exists, otherwise the scheduled pattern
        matches.add(new TripAndPattern(trip, transitService.findPattern(trip, serviceDate)));
      }
    }

    if (matches.isEmpty()) {
      throw UpdateException.of(tripReference.tripId(), UpdateErrorType.NO_FUZZY_TRIP_MATCH);
    }

    if (matches.size() > 1) {
      LOG.warn("Multiple fuzzy matches found ({}), skipping all: {}", matches.size(), matches);
      throw UpdateException.of(tripReference.tripId(), UpdateErrorType.MULTIPLE_FUZZY_TRIP_MATCHES);
    }

    TripAndPattern match = matches.iterator().next();
    LOG.debug(
      "Fuzzy matched trip {} on pattern {}",
      match.trip().getId(),
      match.tripPattern().getId()
    );
    return match;
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
