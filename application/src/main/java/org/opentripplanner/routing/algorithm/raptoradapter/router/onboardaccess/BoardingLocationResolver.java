package org.opentripplanner.routing.algorithm.raptoradapter.router.onboardaccess;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.annotation.Nullable;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.raptor.spi.RaptorTimeTable;
import org.opentripplanner.routing.algorithm.raptoradapter.transit.TripSchedule;
import org.opentripplanner.routing.algorithm.raptoradapter.transit.request.RaptorRoutingRequestTransitData;
import org.opentripplanner.routing.algorithm.raptoradapter.transit.request.TripPatternForDates;
import org.opentripplanner.routing.api.request.TripLocation;
import org.opentripplanner.routing.api.request.TripOnDateReference;
import org.opentripplanner.routing.api.response.InputField;
import org.opentripplanner.routing.api.response.RoutingError;
import org.opentripplanner.routing.api.response.RoutingErrorCode;
import org.opentripplanner.routing.error.RoutingValidationException;
import org.opentripplanner.transit.model.site.StopLocation;
import org.opentripplanner.transit.model.timetable.Trip;
import org.opentripplanner.transit.service.TransitService;
import org.opentripplanner.utils.time.ServiceDateUtils;

/**
 * Resolves a {@link org.opentripplanner.routing.api.request.TripLocation} to an exact stop index
 * and a stop position in pattern for a given pattern
 */
public class BoardingLocationResolver {

  private final TransitService transitService;

  public BoardingLocationResolver(TransitService transitService) {
    this.transitService = transitService;
  }

  public BoardingLocationInPatternReference resolveInSchedule(
    TripSchedule tripSchedule,
    TripLocation tripLocation
  ) {
    var tripAndServiceDate = resolveTripAndServiceDate(tripLocation.tripOnDateReference());

    Integer targetSeconds = toSecondsSinceStartOfService(
      tripLocation.aimedDepartureTime(),
      tripAndServiceDate.serviceDate
    );

    var stopIndices = getStopIndices(tripLocation.stopLocationId());

    return getBoardingLocationInSchedule(
      tripSchedule,
      tripAndServiceDate,
      targetSeconds,
      stopIndices
    );
  }

  public TripAndServiceDate resolveTripAndServiceDate(TripOnDateReference reference) {
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

  public RaptorTimeTable<TripSchedule> getRaptorTimetableForTripLocation(
    RaptorRoutingRequestTransitData raptorRequestTransitData,
    TripAndServiceDate tripAndServiceDate,
    FeedScopedId stopLocationId
  ) {
    var raptorTimetables = getRaptorTimetablesForStopLocation(
      raptorRequestTransitData,
      stopLocationId
    );

    return raptorTimetables
      .stream()
      .filter(patternForDates ->
        patternForDates
          .tripPatternForDate(tripAndServiceDate.serviceDate)
          .tripTimes()
          .stream()
          .anyMatch(tripTime -> tripTime.getTrip().getId().equals(tripAndServiceDate.trip.getId()))
      )
      .findFirst()
      .orElseThrow(() ->
        new IllegalArgumentException(
          "No trip pattern on date %s for trip %s".formatted(
            tripAndServiceDate.serviceDate,
            tripAndServiceDate.trip
          )
        )
      );
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

  private BoardingLocationInPatternReference getBoardingLocationInSchedule(
    TripSchedule tripSchedule,
    TripAndServiceDate tripAndServiceDate,
    @Nullable Integer targetTimeSeconds,
    Collection<Integer> stopIndices
  ) {
    var targetTrip = tripSchedule.getOriginalTripTimes().getTrip();

    BoardingLocationInPatternReference tripLocationReference = null;
    if (targetTrip.getId().equals(tripAndServiceDate.trip.getId())) {
      if (targetTimeSeconds == null) {
        tripLocationReference = findTripLocationInSchedule(tripSchedule, stopIndices);
      } else {
        tripLocationReference = findTripLocationInScheduleAtTime(
          tripSchedule,
          stopIndices,
          targetTimeSeconds
        );
      }
    }

    if (tripLocationReference == null) {
      throw new IllegalArgumentException(
        "Could not find a stop position on %s at %s seconds for trip %s".formatted(
          tripAndServiceDate.serviceDate,
          targetTimeSeconds,
          tripAndServiceDate.trip
        )
      );
    }

    int lastStopPos = tripSchedule.pattern().numberOfStopsInPattern() - 1;
    if (tripLocationReference.stopPositionInPattern == lastStopPos) {
      throw new IllegalArgumentException(
        "Cannot board at the last stop of trip %s — no further travel is possible".formatted(
          tripAndServiceDate.trip
        )
      );
    }

    return tripLocationReference;
  }

  /**
   * Find an exact trip location within a trip schedule by looking for the stop position that
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
   *                                    {@link #findTripLocationInScheduleAtTime} should be used
   *                                    instead to disambiguate.
   */
  private BoardingLocationInPatternReference findTripLocationInSchedule(
    TripSchedule tripSchedule,
    Collection<Integer> stopIndices
  ) {
    List<BoardingLocationInPatternReference> stopPositions = new ArrayList<>();
    for (int stopIndex : stopIndices) {
      for (int stopPos : tripSchedule.findDepartureStopPositions(0, stopIndex)) {
        int boardingTime = tripSchedule.getOriginalTripTimes().getScheduledDepartureTime(stopPos);
        stopPositions.add(new BoardingLocationInPatternReference(stopIndex, stopPos, boardingTime));
      }
    }

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

  /**
   * Find the exact trip location position in a trip schedule at a given boarding time. The time
   * must match exactly. This method works like {@link #findTripLocationInSchedule} with the
   * additional matching criteria that the departure time must exactly match the input boarding
   * time. It can therefore disambiguate between multiple visits to the same stop on ring lines.
   */
  private BoardingLocationInPatternReference findTripLocationInScheduleAtTime(
    TripSchedule tripSchedule,
    Collection<Integer> stopIndices,
    int boardingTimeSeconds
  ) {
    for (int stopIndex : stopIndices) {
      for (int stopPos : tripSchedule.findDepartureStopPositions(boardingTimeSeconds, stopIndex)) {
        if (tripSchedule.departure(stopPos) == boardingTimeSeconds) {
          return new BoardingLocationInPatternReference(stopIndex, stopPos, boardingTimeSeconds);
        }
      }
    }

    throw new IllegalArgumentException(
      "Could not find a stop position in schedule %s for any of the stop indexes in %s".formatted(
        tripSchedule,
        stopIndices
      )
    );
  }

  public record BoardingLocationInPatternReference(
    int stopIndex,
    int stopPositionInPattern,
    int boardingTimeSeconds
  ) {}

  public record TripAndServiceDate(Trip trip, LocalDate serviceDate) {}
}
