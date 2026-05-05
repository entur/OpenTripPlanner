package org.opentripplanner.routing.algorithm.raptoradapter.router.onboardaccess;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import javax.annotation.Nullable;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.routing.api.response.InputField;
import org.opentripplanner.routing.api.response.RoutingError;
import org.opentripplanner.routing.api.response.RoutingErrorCode;
import org.opentripplanner.routing.error.RoutingValidationException;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.timetable.Trip;
import org.opentripplanner.transit.service.TransitService;
import org.opentripplanner.utils.time.ServiceDateUtils;

/**
 * Resolves the boarding time for a start-on-board trip location as an {@link Instant}. This is
 * called early in the pipeline (before Raptor routing) to set the request's dateTime.
 *
 * <p>Unlike {@link StartOnBoardAccessResolver}, this class does not need the Raptor pattern index
 * and operates entirely on {@link TransitService}.
 */
public class StartOnBoardBoardingTimeResolver {

  private final TransitService transitService;

  public StartOnBoardBoardingTimeResolver(TransitService transitService) {
    this.transitService = transitService;
  }

  public Instant resolve(
    TripAndServiceDate tripAndServiceDate,
    FeedScopedId stopLocationId,
    @Nullable Instant aimedDepartureTime,
    ZoneId timeZone
  ) {
    var trip = tripAndServiceDate.trip();
    var serviceDate = tripAndServiceDate.serviceDate();

    var tripPattern = transitService.findPattern(trip, serviceDate);
    if (tripPattern == null) {
      throw new IllegalArgumentException(
        "No pattern found for trip %s on date %s".formatted(trip.getId(), serviceDate)
      );
    }

    var serviceDateStart = ServiceDateUtils.asStartOfService(serviceDate, timeZone);
    Integer targetSeconds = aimedDepartureTime == null
      ? null
      : (int) (aimedDepartureTime.getEpochSecond() - serviceDateStart.toEpochSecond());

    int stopPosInPattern = findStopPositionInPattern(
      tripPattern,
      stopLocationId,
      trip,
      serviceDate,
      targetSeconds
    );

    var tripTimes = transitService.findTimetable(tripPattern, serviceDate).getTripTimes(trip);
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
    return serviceDateStart.plusSeconds(boardingTime).toInstant();
  }

  private int findStopPositionInPattern(
    TripPattern tripPattern,
    FeedScopedId stopLocationId,
    Trip trip,
    java.time.LocalDate serviceDate,
    @Nullable Integer aimedDepartureTime
  ) {
    int stopPos = aimedDepartureTime != null
      ? findStopPositionByDepartureTime(
          tripPattern,
          stopLocationId,
          trip,
          serviceDate,
          aimedDepartureTime
        )
      : findSingleStopPosition(tripPattern, stopLocationId, trip);

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

  private int findStopPositionByDepartureTime(
    TripPattern tripPattern,
    FeedScopedId stopLocationId,
    Trip trip,
    java.time.LocalDate serviceDate,
    int targetSeconds
  ) {
    var tripTimes = transitService.findTimetable(tripPattern, serviceDate).getTripTimes(trip);
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
      if (tripTimes.getScheduledDepartureTime(i) == targetSeconds) {
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
}
