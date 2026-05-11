package org.opentripplanner.routing.algorithm.raptoradapter.router.startonboardaccess;

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

/**
 * Resolves the boarding time for a start-on-board trip location as seconds since service day
 * start. This is called early in the pipeline (before Raptor routing) to set the request's
 * dateTime.
 *
 * <p>Unlike {@link StartOnBoardAccessResolver}, this class does not need the Raptor pattern index
 * and operates entirely on {@link TransitService}.
 */
public class TripLocationResolver {

  private final TransitService transitService;

  public TripLocationResolver(TransitService transitService) {
    this.transitService = transitService;
  }

  public int resolve(
    TripAndServiceDate tripAndServiceDate,
    FeedScopedId stopOrStationId,
    @Nullable Integer aimedDepartureTime
  ) {
    var trip = tripAndServiceDate.trip();
    var serviceDate = tripAndServiceDate.serviceDate();

    var tripPattern = transitService.findPattern(trip, serviceDate);
    if (tripPattern == null) {
      throw new IllegalArgumentException(
        "No pattern found for trip %s on date %s".formatted(trip.getId(), serviceDate)
      );
    }

    int stopPosInPattern = findStopPositionInPattern(
      tripPattern,
      stopOrStationId,
      trip,
      serviceDate,
      aimedDepartureTime
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

    return tripTimes.getScheduledDepartureTime(stopPosInPattern);
  }

  private int findStopPositionInPattern(
    TripPattern tripPattern,
    FeedScopedId stopOrStationId,
    Trip trip,
    java.time.LocalDate serviceDate,
    @Nullable Integer aimedDepartureTime
  ) {
    int stopPos = aimedDepartureTime != null
      ? findStopPositionByDepartureTime(
          tripPattern,
          stopOrStationId,
          trip,
          serviceDate,
          aimedDepartureTime
        )
      : findSingleStopPosition(tripPattern, stopOrStationId, trip);

    int lastStopPos = tripPattern.numberOfStops() - 1;
    if (stopPos == lastStopPos) {
      throw new IllegalArgumentException(
        "Cannot board at the last stop %s of trip %s — no further travel is possible".formatted(
          stopOrStationId,
          trip.getId()
        )
      );
    }

    return stopPos;
  }

  private int findStopPositionByDepartureTime(
    TripPattern tripPattern,
    FeedScopedId stopOrStationId,
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
        !stop.getId().equals(stopOrStationId) && !stop.getStationOrStopId().equals(stopOrStationId)
      ) {
        continue;
      }
      if (tripTimes.getScheduledDepartureTime(i) == targetSeconds) {
        return i;
      }
    }

    throw new IllegalArgumentException(
      "No stop %s with the provided departure time found in pattern for trip %s".formatted(
        stopOrStationId,
        trip.getId()
      )
    );
  }

  private int findSingleStopPosition(
    TripPattern tripPattern,
    FeedScopedId stopOrStationId,
    Trip trip
  ) {
    var stopPositionInPattern = -1;
    for (int i = 0; i < tripPattern.numberOfStops(); i++) {
      var stop = tripPattern.getStop(i);
      if (
        stop.getId().equals(stopOrStationId) || stop.getStationOrStopId().equals(stopOrStationId)
      ) {
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
        "Stop or station %s not found in pattern for trip %s".formatted(
          stopOrStationId,
          trip.getId()
        )
      );
    }

    return stopPositionInPattern;
  }
}
