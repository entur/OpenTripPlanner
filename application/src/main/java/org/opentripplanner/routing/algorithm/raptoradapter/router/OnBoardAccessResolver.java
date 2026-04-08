package org.opentripplanner.routing.algorithm.raptoradapter.router;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.raptor.spi.RaptorTimeTable;
import org.opentripplanner.raptor.spi.RaptorTripScheduleReference;
import org.opentripplanner.raptor.util.IntIterators;
import org.opentripplanner.routing.algorithm.raptoradapter.transit.RoutingOnBoardAccess;
import org.opentripplanner.routing.algorithm.raptoradapter.transit.TripSchedule;
import org.opentripplanner.routing.algorithm.raptoradapter.transit.request.RaptorRoutingRequestTransitData;
import org.opentripplanner.routing.algorithm.raptoradapter.transit.request.TripPatternForDates;
import org.opentripplanner.routing.api.request.TripLocation;
import org.opentripplanner.routing.api.request.TripOnDateReference;
import org.opentripplanner.routing.api.response.InputField;
import org.opentripplanner.routing.api.response.RoutingError;
import org.opentripplanner.routing.api.response.RoutingErrorCode;
import org.opentripplanner.routing.error.RoutingValidationException;
import org.opentripplanner.transit.model.network.RoutingTripPattern;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.site.StopLocation;
import org.opentripplanner.transit.model.timetable.Trip;
import org.opentripplanner.transit.service.TransitService;
import org.opentripplanner.utils.time.ServiceDateUtils;

/**
 * Resolves a {@link TripLocation} into a {@link RoutingOnBoardAccess} by looking up the trip,
 * pattern, stop position, and trip schedule index in the transit model.
 */
public class OnBoardAccessResolver {

  private final TransitService transitService;

  public OnBoardAccessResolver(TransitService transitService) {
    this.transitService = transitService;
  }

  /**
   * Resolve the given trip location to a {@link RoutingOnBoardAccess} containing the on-board
   * access and the service date.
   *
   * @param tripLocation the on-board location to resolve
   * @param raptorRequestTransitData the raptorRequestTransitDataProvider
   * @return a {@link RoutingOnBoardAccess} for the given trip location
   * @throws IllegalArgumentException if the trip, stop, or departure time is invalid
   * @throws RoutingValidationException if the on-board location is ambiguous
   */
  public RoutingOnBoardAccess resolve(
    TripLocation tripLocation,
    RaptorRoutingRequestTransitData raptorRequestTransitData
  ) {
    var tripAndServiceDate = resolveTripAndServiceDate(tripLocation.tripOnDateReference());
    var trip = tripAndServiceDate.trip();
    var serviceDate = tripAndServiceDate.serviceDate();

    var raptorTimetables = getRaptorTimetablesForStopLocation(
      raptorRequestTransitData,
      tripLocation.stopLocationId()
    );

    var raptorTimetable = raptorTimetables
      .stream()
      .filter(patternForDates ->
        patternForDates
          .tripPatternForDate(serviceDate)
          .tripTimes()
          .stream()
          .anyMatch(tripTime -> tripTime.getTrip().getId().equals(trip.getId()))
      )
      .findFirst()
      .orElseThrow(() ->
        new IllegalArgumentException(
          "No trip pattern on date %s for trip %s".formatted(serviceDate, trip)
        )
      );

    Integer targetSeconds = toSecondsSinceStartOfService(
      tripLocation.aimedDepartureTime(),
      serviceDate
    );
    var stopIndices = getStopIndices(tripLocation.stopLocationId());
    int stopPosInPattern = getStopPositionInPattern(
      raptorTimetable,
      trip,
      serviceDate,
      targetSeconds,
      stopIndices
    );

    var tripPatternForServiceDate = raptorTimetable.tripPatternForDate(serviceDate);
    RoutingTripPattern routingPattern = tripPatternForServiceDate.getTripPattern();

    var tripTimes = tripPatternForServiceDate
      .tripTimes()
      .stream()
      .filter(t -> t.getTrip().getId().equals(trip.getId()))
      .findFirst()
      .orElseThrow(() ->
        new IllegalArgumentException(
          "Trip %s not found in timetable on date %s".formatted(trip.getId(), serviceDate)
        )
      );

    int boardingTime = tripTimes.getScheduledDepartureTime(stopPosInPattern);

    var tripScheduleIndexReference = getTripScheduleReference(
      raptorRequestTransitData,
      raptorTimetable,
      trip,
      serviceDate
    );

    return new RoutingOnBoardAccess(
      tripScheduleIndexReference,
      stopPosInPattern,
      routingPattern.stopIndex(stopPosInPattern),
      boardingTime
    );
  }

  private Collection<Integer> getStopIndices(FeedScopedId stopLocationId) {
    var stop = transitService.getRegularStop(stopLocationId);
    if (stop != null) {
      return List.of(stop.getIndex());
    }

    var station = transitService.getStation(stopLocationId);
    if (station != null) {
      return station.getChildStops().stream().map(StopLocation::getIndex).toList();
    }

    throw new IllegalArgumentException(
      "No stop or station found with id %s".formatted(stopLocationId)
    );
  }

  /**
   * Resolve the boarding time for an on-board trip location as an {@link Instant}. This uses
   * the transit service to look up the trip, pattern, and scheduled departure time — it does
   * not need the Raptor pattern index, so it can be called early in the pipeline to set the
   * request's dateTime before the search begins.
   */
  public Instant resolveBoardingDateTime(TripLocation tripLocation, ZoneId timeZone) {
    var tripAndServiceDate = resolveTripAndServiceDate(tripLocation.tripOnDateReference());
    var trip = tripAndServiceDate.trip();
    var serviceDate = tripAndServiceDate.serviceDate();

    var tripPattern = transitService.findPattern(trip, serviceDate);
    if (tripPattern == null) {
      throw new IllegalArgumentException(
        "No pattern found for trip %s on date %s".formatted(trip.getId(), serviceDate)
      );
    }

    Integer targetSeconds = toSecondsSinceStartOfService(
      tripLocation.aimedDepartureTime(),
      serviceDate
    );
    int stopPosInPattern = findStopPositionInPattern(
      tripPattern,
      tripLocation.stopLocationId(),
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

  private TripAndServiceDate resolveTripAndServiceDate(TripOnDateReference reference) {
    if (reference.tripOnServiceDateId() != null) {
      var tripOnServiceDate = transitService.getTripOnServiceDate(reference.tripOnServiceDateId());
      if (tripOnServiceDate == null) {
        throw new IllegalArgumentException(
          "TripOnServiceDate not found: " + reference.tripOnServiceDateId()
        );
      }
      return new TripAndServiceDate(
        tripOnServiceDate.getTrip(),
        tripOnServiceDate.getServiceDate()
      );
    } else if (reference.tripIdOnServiceDate() != null) {
      var tripIdAndDate = reference.tripIdOnServiceDate();
      var trip = transitService.getTrip(tripIdAndDate.tripId());
      if (trip == null) {
        throw new IllegalArgumentException("Trip not found: " + tripIdAndDate.tripId());
      }
      return new TripAndServiceDate(trip, tripIdAndDate.serviceDate());
    }

    throw new IllegalArgumentException(
      "Either tripOnServiceDateId or tripIdOnServiceDate must be set on TripOnDateReference"
    );
  }

  /**
   * Convert an instant to seconds since start-of-service of the given service date in the
   * transit system's timezone. Returns null if the instant is null.
   * <p>
   * OTP's internal timetable times (e.g. from {@code TripTimes.getScheduledDepartureTime}) are
   * relative to noon-minus-12h (start of service), not midnight. On DST transition days these
   * differ by one hour.
   */
  private Integer toSecondsSinceStartOfService(Instant instant, LocalDate serviceDate) {
    if (instant == null) {
      return null;
    }
    ZoneId timeZone = transitService.getTimeZone();
    long startOfServiceEpochSecond = ServiceDateUtils.asStartOfService(
      serviceDate,
      timeZone
    ).toEpochSecond();
    return (int) (instant.getEpochSecond() - startOfServiceEpochSecond);
  }

  /**
   * Find the stop position in the pattern. The {@code stopLocationId} can refer to either a
   * regular stop (quay) or a station (stop place). If the resolved stops appear more than once
   * and no {@code aimedDepartureTime} is provided, a {@link RoutingValidationException} with
   * {@link RoutingErrorCode#ON_BOARD_LOCATION_MISSING_SCHEDULED_DEPARTURE_TIME} is thrown, signaling that
   * a retry with an {@code aimedDepartureTime} is needed. When an {@code aimedDepartureTime} is provided, it is always
   * validated against the timetable — even for unique stops.
   */
  private int findStopPositionInPattern(
    TripPattern tripPattern,
    FeedScopedId stopLocationId,
    Trip trip,
    LocalDate serviceDate,
    Integer aimedDepartureTime
  ) {
    int stopPos;
    // When an aimedDepartureTime is provided, always use it
    // That way, we validate that it matches the timetable data
    if (aimedDepartureTime != null) {
      stopPos = findStopPositionByDepartureTime(
        tripPattern,
        stopLocationId,
        trip,
        serviceDate,
        aimedDepartureTime
      );
    } else {
      stopPos = findSingleStopPosition(tripPattern, stopLocationId, trip);
    }

    int lastStopPos = tripPattern.numberOfStops() - 1;
    if (stopPos == lastStopPos) {
      throw new IllegalArgumentException(
        "Cannot board at the last stop %s of trip %s — no further travel is possible".formatted(
          stopLocationId,
          trip.getId()
        )
      );
    }

    return stopPos;
  }

  /**
   * Find the stop position by matching the aimed departure time (in seconds since midnight,
   * including day offset) among all occurrences of the stop in the pattern.
   */
  private int findStopPositionByDepartureTime(
    TripPattern tripPattern,
    FeedScopedId stopLocationId,
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
      var stop = tripPattern.getStop(i);
      if (
        !stop.getId().equals(stopLocationId) && !stop.getStationOrStopId().equals(stopLocationId)
      ) {
        continue;
      }
      int depTime = tripTimes.getScheduledDepartureTime(i);
      if (depTime == targetSeconds) {
        return i;
      }
    }

    throw new IllegalArgumentException(
      "No stop %s with the provided departure time found in pattern for trip %s".formatted(
        stopLocationId,
        trip.getId()
      )
    );
  }

  /**
   * Find a single unambiguous stop position by id
   */
  private int findSingleStopPosition(
    TripPattern tripPattern,
    FeedScopedId stopLocationId,
    Trip trip
  ) {
    var stopPositionInPattern = -1;
    for (int i = 0; i < tripPattern.numberOfStops(); i++) {
      var stop = tripPattern.getStop(i);
      if (stop.getId().equals(stopLocationId) || stop.getStationOrStopId().equals(stopLocationId)) {
        if (stopPositionInPattern >= 0) {
          // Multiple occurrences — need scheduled departure time to disambiguate
          throw new RoutingValidationException(
            List.of(
              new RoutingError(
                RoutingErrorCode.ON_BOARD_LOCATION_MISSING_SCHEDULED_DEPARTURE_TIME,
                InputField.FROM_PLACE
              )
            )
          );
        }

        stopPositionInPattern = i;
      }
    }

    if (stopPositionInPattern < 0) {
      throw new IllegalArgumentException(
        "Stop location %s not found in pattern for trip %s".formatted(stopLocationId, trip.getId())
      );
    }

    return stopPositionInPattern;
  }

  private Collection<TripPatternForDates> getRaptorTimetablesForStopLocation(
    RaptorRoutingRequestTransitData raptorRequestTransitData,
    FeedScopedId stopLocationId
  ) {
    var stop = transitService.getRegularStop(stopLocationId);
    var station = transitService.getStation(stopLocationId);
    if (stop == null && station == null) {
      throw new IllegalArgumentException(
        "No stop or station found with id %s".formatted(stopLocationId)
      );
    }
    if (stop != null) {
      return raptorRequestTransitData.activeTripPatternsPerStop(stop.getIndex());
    } else {
      var stopsInStation = station.getChildStops().stream().map(StopLocation::getIndex).toList();
      return raptorRequestTransitData.activeTripPatternsByStopIndices(stopsInStation);
    }
  }

  private RaptorTripScheduleReference getTripScheduleReference(
    RaptorRoutingRequestTransitData raptorRoutingRequestTransitData,
    RaptorTimeTable<TripSchedule> timetable,
    Trip trip,
    LocalDate serviceDate
  ) {
    var scheduleIndexIterator = IntIterators.intIncIterator(0, timetable.numberOfTripSchedules());

    while (scheduleIndexIterator.hasNext()) {
      int tripScheduleIndex = scheduleIndexIterator.next();
      var tripSchedule = timetable.getTripSchedule(tripScheduleIndex);
      if (!tripSchedule.getServiceDate().equals(serviceDate)) {
        continue;
      }
      var targetTrip = tripSchedule.getOriginalTripTimes().getTrip();
      if (targetTrip.getId().equals(trip.getId())) {
        return raptorRoutingRequestTransitData.tripScheduleReference(tripSchedule);
      }
    }

    throw new IllegalArgumentException(
      "No trip pattern on date %s for trip %s".formatted(serviceDate, trip)
    );
  }

  private int getStopPositionInPattern(
    RaptorTimeTable<TripSchedule> timetable,
    Trip trip,
    LocalDate serviceDate,
    Integer targetTimeSeconds,
    Collection<Integer> stopIndices
  ) {
    var scheduleIndexIterator = IntIterators.intIncIterator(0, timetable.numberOfTripSchedules());

    while (scheduleIndexIterator.hasNext()) {
      int tripScheduleIndex = scheduleIndexIterator.next();
      var tripSchedule = timetable.getTripSchedule(tripScheduleIndex);
      if (!tripSchedule.getServiceDate().equals(serviceDate)) {
        continue;
      }
      var targetTrip = tripSchedule.getOriginalTripTimes().getTrip();

      int stopPos = -1;
      if (targetTrip.getId().equals(trip.getId())) {
        if (targetTimeSeconds == null) {
          stopPos = findStopPositionInTripSchedule(tripSchedule, stopIndices);
        } else {
          stopPos = findStopPositionInTripScheduleAtTime(
            tripSchedule,
            stopIndices,
            targetTimeSeconds
          );
        }
      }

      int lastStopPos = tripSchedule.pattern().numberOfStopsInPattern() - 1;
      if (stopPos == lastStopPos) {
        throw new IllegalArgumentException(
          "Cannot board at the last stop of trip %s — no further travel is possible".formatted(trip)
        );
      }
      if (stopPos != -1) {
        return stopPos;
      }
    }

    throw new IllegalArgumentException(
      "Could not find a stop position on %s at %s seconds for trip %s".formatted(
        serviceDate,
        targetTimeSeconds,
        trip
      )
    );
  }

  private int findStopPositionInTripScheduleAtTime(
    TripSchedule tripSchedule,
    Collection<Integer> stopIndices,
    int targetTimeSeconds
  ) {
    var stopPositions = stopIndices
      .stream()
      .flatMap(stop -> tripSchedule.findDepartureStopPositions(targetTimeSeconds, stop).stream())
      .toList();

    for (int stopPos : stopPositions) {
      if (tripSchedule.departure(stopPos) == targetTimeSeconds) {
        return stopPos;
      }
    }

    throw new IllegalArgumentException(
      "Could not find a stop position in schedule %s for any of the stop indexes in %s".formatted(
        tripSchedule,
        stopIndices
      )
    );
  }

  /**
   * Find a single stop position within a trip schedule by looking for the stop position that
   * matches one of the given stop indices. This method takes multiple stop indices to search with
   * in order to support stations. In that case, the caller should pass the child stop indices of
   * the station. It is not intended to pass multiple stopIndices across multiple stations.
   *
   * @param tripSchedule the trip schedule to search
   * @param stopIndices a single stop index in case of a stop, or multiple stop indices in case of
   *                    a station with multiple child stops
   *
   * @throws IllegalArgumentException if a tripSchedule doesn't contain any index in stopIndices
   * @throws RoutingValidationException if the search returns more than one stop position. This
   *                                    means the trip schedule visits the same stop or station
   *                                    multiple times and
   *                                    {@link #findStopPositionInTripScheduleAtTime} should be used
   *                                    instead to disambiguate.
   */
  private int findStopPositionInTripSchedule(
    TripSchedule tripSchedule,
    Collection<Integer> stopIndices
  ) {
    var stopPositions = stopIndices
      .stream()
      .flatMap(stop -> tripSchedule.findDepartureStopPositions(0, stop).stream())
      .toList();

    if (stopPositions.isEmpty()) {
      throw new IllegalArgumentException(
        "Could not find a stop position in schedule %s for any of the stop indexes in %s".formatted(
          tripSchedule,
          stopIndices
        )
      );
    }

    if (stopPositions.size() > 1) {
      throw new RoutingValidationException(
        List.of(
          new RoutingError(
            RoutingErrorCode.ON_BOARD_LOCATION_MISSING_SCHEDULED_DEPARTURE_TIME,
            InputField.FROM_PLACE
          )
        )
      );
    }

    return stopPositions.getFirst();
  }

  private record TripAndServiceDate(Trip trip, LocalDate serviceDate) {}
}
