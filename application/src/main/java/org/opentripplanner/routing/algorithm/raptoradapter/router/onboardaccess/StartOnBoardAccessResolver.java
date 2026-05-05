package org.opentripplanner.routing.algorithm.raptoradapter.router.onboardaccess;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.raptor.spi.RaptorTimeTable;
import org.opentripplanner.raptor.util.IntIterators;
import org.opentripplanner.routing.algorithm.raptoradapter.transit.TripPatternForDate;
import org.opentripplanner.routing.algorithm.raptoradapter.transit.TripSchedule;
import org.opentripplanner.routing.algorithm.raptoradapter.transit.mappers.LookupStopIndexCallback;
import org.opentripplanner.routing.algorithm.raptoradapter.transit.request.RaptorRoutingRequestTransitData;
import org.opentripplanner.routing.algorithm.raptoradapter.transit.request.TripPatternForDates;
import org.opentripplanner.routing.api.response.InputField;
import org.opentripplanner.routing.api.response.RoutingError;
import org.opentripplanner.routing.api.response.RoutingErrorCode;
import org.opentripplanner.routing.error.RoutingValidationException;
import org.opentripplanner.utils.time.ServiceDateUtils;

/**
 * Resolves a {@link TripAndServiceDate} into a {@link RoutingOnBoardAccess} by locating the trip
 * in the Raptor timetable and finding the exact stop position within the trip schedule.
 *
 * <p>Callers must first resolve the trip via {@link TripAndServiceDateResolver} and provide a
 * {@link LookupStopIndexCallback} to resolve the stop location to raptor stop indices.
 */
public class StartOnBoardAccessResolver {

  private final RaptorRoutingRequestTransitData raptorRequestTransitData;

  public StartOnBoardAccessResolver(RaptorRoutingRequestTransitData raptorRequestTransitData) {
    this.raptorRequestTransitData = raptorRequestTransitData;
  }

  public RoutingOnBoardAccess resolve(
    TripAndServiceDate tripAndServiceDate,
    FeedScopedId stopLocationId,
    LookupStopIndexCallback stopIndexCallback,
    @Nullable Instant aimedDepartureTime,
    ZoneId timeZone
  ) {
    var stopIndices = stopIndexCallback.lookupStopLocationIndexes(stopLocationId).boxed().toList();

    var raptorTimetable = getRaptorTimetableForTrip(stopIndices, tripAndServiceDate);
    var tripSchedule = findTripScheduleInTimetable(raptorTimetable, tripAndServiceDate);
    var tripScheduleIndexReference = raptorRequestTransitData.tripScheduleReference(tripSchedule);

    Integer targetSeconds = aimedDepartureTime == null
      ? null
      : (int) (aimedDepartureTime.getEpochSecond() -
          ServiceDateUtils.asStartOfService(
            tripAndServiceDate.serviceDate(),
            timeZone
          ).toEpochSecond());

    var boardingLocation = getBoardingLocationInSchedule(
      tripSchedule,
      tripAndServiceDate,
      targetSeconds,
      stopIndices
    );

    return new RoutingOnBoardAccess(tripScheduleIndexReference, boardingLocation);
  }

  private RaptorTimeTable<TripSchedule> getRaptorTimetableForTrip(
    Collection<Integer> stopIndices,
    TripAndServiceDate tripAndServiceDate
  ) {
    return raptorRequestTransitData
      .activeTripPatternsByStopIndices(stopIndices)
      .stream()
      .filter(patternForDates ->
        tripPatternForDate(patternForDates, tripAndServiceDate.serviceDate())
          .map(p ->
            p
              .tripTimes()
              .stream()
              .anyMatch(tt -> tt.getTrip().getId().equals(tripAndServiceDate.trip().getId()))
          )
          .orElse(false)
      )
      .findFirst()
      .orElseThrow(() ->
        new IllegalArgumentException(
          "No trip pattern on date %s for trip %s".formatted(
            tripAndServiceDate.serviceDate(),
            tripAndServiceDate.trip()
          )
        )
      );
  }

  private Optional<TripPatternForDate> tripPatternForDate(
    TripPatternForDates tripPatternForDates,
    LocalDate serviceDate
  ) {
    var dayIndexIterator = tripPatternForDates.tripPatternForDatesIndexIterator(true);
    while (dayIndexIterator.hasNext()) {
      var dayIndex = dayIndexIterator.next();
      var tripPattern = tripPatternForDates.tripPatternForDate(dayIndex);
      if (tripPattern.getServiceDate().equals(serviceDate)) {
        return Optional.of(tripPattern);
      }
    }
    return Optional.empty();
  }

  private TripSchedule findTripScheduleInTimetable(
    RaptorTimeTable<TripSchedule> timetable,
    TripAndServiceDate tripAndServiceDate
  ) {
    var scheduleIndexIterator = IntIterators.intIncIterator(0, timetable.numberOfTripSchedules());
    while (scheduleIndexIterator.hasNext()) {
      int tripScheduleIndex = scheduleIndexIterator.next();
      var tripSchedule = timetable.getTripSchedule(tripScheduleIndex);
      if (!tripSchedule.getServiceDate().equals(tripAndServiceDate.serviceDate())) {
        continue;
      }
      if (
        tripSchedule
          .getOriginalTripTimes()
          .getTrip()
          .getId()
          .equals(tripAndServiceDate.trip().getId())
      ) {
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

  private BoardingLocationInPatternReference getBoardingLocationInSchedule(
    TripSchedule tripSchedule,
    TripAndServiceDate tripAndServiceDate,
    @Nullable Integer targetTimeSeconds,
    Collection<Integer> stopIndices
  ) {
    var targetTrip = tripSchedule.getOriginalTripTimes().getTrip();

    BoardingLocationInPatternReference tripLocationReference = null;
    if (targetTrip.getId().equals(tripAndServiceDate.trip().getId())) {
      tripLocationReference = targetTimeSeconds == null
        ? findTripLocationInSchedule(tripSchedule, stopIndices)
        : findTripLocationInScheduleAtTime(tripSchedule, stopIndices, targetTimeSeconds);
    }

    if (tripLocationReference == null) {
      throw new IllegalArgumentException(
        "Could not find a stop position on %s at %s seconds for trip %s".formatted(
          tripAndServiceDate.serviceDate(),
          targetTimeSeconds,
          tripAndServiceDate.trip()
        )
      );
    }

    int lastStopPos = tripSchedule.pattern().numberOfStopsInPattern() - 1;
    if (tripLocationReference.stopPositionInPattern() == lastStopPos) {
      throw new IllegalArgumentException(
        "Cannot board at the last stop of trip %s — no further travel is possible".formatted(
          tripAndServiceDate.trip()
        )
      );
    }

    return tripLocationReference;
  }

  /**
   * Find an exact trip location within a trip schedule by looking for the stop position that
   * matches one of the given stop indices. Throws {@link RoutingValidationException} if the stop
   * appears more than once (ring line) — callers should retry with an aimed departure time.
   */
  private BoardingLocationInPatternReference findTripLocationInSchedule(
    TripSchedule tripSchedule,
    Collection<Integer> stopIndices
  ) {
    List<BoardingLocationInPatternReference> stopPositions = new ArrayList<>();
    for (int stopIndex : stopIndices) {
      for (int stopPos : tripSchedule.findDepartureStopPositions(0, stopIndex)) {
        if (tripSchedule.pattern().boardingPossibleAt(stopPos)) {
          int boardingTime = tripSchedule.getOriginalTripTimes().getScheduledDepartureTime(stopPos);
          stopPositions.add(
            new BoardingLocationInPatternReference(stopIndex, stopPos, boardingTime)
          );
        }
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
   * Find the exact trip location position in a trip schedule at a given boarding time.
   * Disambiguates multiple visits to the same stop (ring lines).
   */
  private BoardingLocationInPatternReference findTripLocationInScheduleAtTime(
    TripSchedule tripSchedule,
    Collection<Integer> stopIndices,
    int boardingTimeSeconds
  ) {
    for (int stopIndex : stopIndices) {
      for (int stopPos : tripSchedule.findDepartureStopPositions(boardingTimeSeconds, stopIndex)) {
        if (
          tripSchedule.departure(stopPos) == boardingTimeSeconds &&
          tripSchedule.pattern().boardingPossibleAt(stopPos)
        ) {
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
}
