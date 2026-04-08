package org.opentripplanner.routing.algorithm.raptoradapter.router.onboardaccess;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.raptor.spi.RaptorTimeTable;
import org.opentripplanner.raptor.util.IntIterators;
import org.opentripplanner.routing.algorithm.raptoradapter.transit.RoutingOnBoardAccess;
import org.opentripplanner.routing.algorithm.raptoradapter.transit.TripSchedule;
import org.opentripplanner.routing.algorithm.raptoradapter.transit.request.RaptorRoutingRequestTransitData;
import org.opentripplanner.routing.api.request.TripLocation;
import org.opentripplanner.routing.api.response.InputField;
import org.opentripplanner.routing.api.response.RoutingError;
import org.opentripplanner.routing.api.response.RoutingErrorCode;
import org.opentripplanner.routing.error.RoutingValidationException;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.timetable.Trip;
import org.opentripplanner.transit.service.TransitService;
import org.opentripplanner.utils.time.ServiceDateUtils;

/**
 * Resolves a {@link TripLocation} into a {@link RoutingOnBoardAccess} by looking up the trip,
 * pattern, stop position, and trip schedule index in the transit model.
 */
public class StartOnBoardAccessResolver {

  private final TransitService transitService;
  private final BoardingLocationResolver boardingLocationResolver;

  public StartOnBoardAccessResolver(TransitService transitService) {
    this.transitService = transitService;
    boardingLocationResolver = new BoardingLocationResolver(transitService);
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
    // Resolve trip and service date from the TripOnDateReference
    var tripAndServiceDate = boardingLocationResolver.resolveTripAndServiceDate(
      tripLocation.tripOnDateReference()
    );

    // Iterate over the raptor data's timetables for the given stop location to find the timetable
    // that contains the specific trip on the specific service date
    var raptorTimetable = boardingLocationResolver.getRaptorTimetableForTripLocation(
      raptorRequestTransitData,
      tripAndServiceDate,
      tripLocation.stopLocationId()
    );

    // Iterate over trip schedules in raptorTimetable to find the schedule of the target trip
    var tripSchedule = findTripScheduleInTimetable(raptorTimetable, tripAndServiceDate);

    // Get the index reference of the trip schedule
    var tripScheduleIndexReference = raptorRequestTransitData.tripScheduleReference(tripSchedule);

    // Resolve the boarding location in the schedule.
    // That is, the specific stop, its position in the pattern and its boarding time
    var boardingLocationInScheduleReference = boardingLocationResolver.resolveInSchedule(
      tripSchedule,
      tripLocation
    );

    return new RoutingOnBoardAccess(
      tripScheduleIndexReference,
      boardingLocationInScheduleReference
    );
  }

  /**
   * Resolve the boarding time for an on-board trip location as an {@link Instant}. This uses
   * the transit service to look up the trip, pattern, and scheduled departure time — it does
   * not need the Raptor pattern index, so it can be called early in the pipeline to set the
   * request's dateTime before the search begins.
   */
  public Instant resolveBoardingDateTime(TripLocation tripLocation, ZoneId timeZone) {
    // Resolve trip and service date from the TripOnDateReference
    var tripAndServiceDate = boardingLocationResolver.resolveTripAndServiceDate(
      tripLocation.tripOnDateReference()
    );
    var trip = tripAndServiceDate.trip();
    var serviceDate = tripAndServiceDate.serviceDate();

    // Resolve tripPattern from transitService
    // NOTE! We need to get this from transitService instead of raptor timetable data, because we
    // do not have RaptorRoutingRequestTransitData available here
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

  private TripSchedule findTripScheduleInTimetable(
    RaptorTimeTable<TripSchedule> timetable,
    BoardingLocationResolver.TripAndServiceDate tripAndServiceDate
  ) {
    var scheduleIndexIterator = IntIterators.intIncIterator(0, timetable.numberOfTripSchedules());

    while (scheduleIndexIterator.hasNext()) {
      int tripScheduleIndex = scheduleIndexIterator.next();
      var tripSchedule = timetable.getTripSchedule(tripScheduleIndex);
      if (!tripSchedule.getServiceDate().equals(tripAndServiceDate.serviceDate())) {
        continue;
      }
      var targetTrip = tripSchedule.getOriginalTripTimes().getTrip();
      if (targetTrip.getId().equals(tripAndServiceDate.trip().getId())) {
        return tripSchedule;
      }
    }

    throw new IllegalArgumentException(
      "No trip schedule on date %s for trip %s".formatted(
        tripAndServiceDate.serviceDate(),
        tripAndServiceDate.trip()
      )
    );
  }
}
