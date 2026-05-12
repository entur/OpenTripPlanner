package org.opentripplanner.routing.algorithm.raptoradapter.router.startonboardaccess;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Optional;
import org.opentripplanner.raptor.spi.RaptorTimeTable;
import org.opentripplanner.raptor.spi.RaptorTripScheduleReference;
import org.opentripplanner.raptor.util.IntIterators;
import org.opentripplanner.routing.algorithm.raptoradapter.transit.TripPatternForDate;
import org.opentripplanner.routing.algorithm.raptoradapter.transit.TripSchedule;
import org.opentripplanner.routing.algorithm.raptoradapter.transit.request.RaptorRoutingRequestTransitData;
import org.opentripplanner.routing.algorithm.raptoradapter.transit.request.TripPatternForDates;

/**
 * Resolves a {@link RaptorTripScheduleReference} from a {@link TripAndServiceDate} in the Raptor
 * timetable data. Trips in the raptor timetables are indexed by stops, so stop indices must be
 * passed as well to perform the search.
 */
public class TripScheduleIndexResolver {

  private final RaptorRoutingRequestTransitData raptorRequestTransitData;

  /**
   * @param raptorRequestTransitData raptor timetable data
   */
  public TripScheduleIndexResolver(RaptorRoutingRequestTransitData raptorRequestTransitData) {
    this.raptorRequestTransitData = raptorRequestTransitData;
  }

  /**
   * Resolve a {@link RaptorTripScheduleReference} in the Raptor timetable data. A trip schedule
   * reference will be returned if one exists in the raptor timetable that passes through one of the
   * provided stop indices, and the provided stop position allows boarding. If no trip is found, an
   * IllegalArgumentException is thrown.
   *
   * @param tripAndServiceDate the trip and service date to look up
   * @param stopIndices the indices of the stop that the given trip passes through. This supports
   *                    searching on a single stop, or a station with multiple child stops. For a
   *                    single stop, pass a collection with a single stop index, and for a station,
   *                    pass a collection with all child stop indices.
   * @param stopPositionInPattern the stop position in pattern for the stop (or station) the
   *                              trip passes through. For ring-lines, that pass by the same stop
   *                              several times, the stop position in pattern is necessary to rule
   *                              out cases where the stop is canceled on one but not all the
   *                              passes.
   */
  public RaptorTripScheduleReference resolve(
    TripAndServiceDate tripAndServiceDate,
    Collection<Integer> stopIndices,
    int stopPositionInPattern
  ) {
    var raptorTimetable = getRaptorTimetableForTripAtStop(stopIndices, tripAndServiceDate);
    if (!raptorTimetable.boardingPossibleAt(stopPositionInPattern)) {
      throw new IllegalArgumentException(
        "Boarding is not allowed at stop position %d for trip %s".formatted(
          stopPositionInPattern,
          tripAndServiceDate.trip()
        )
      );
    }
    var tripSchedule = findTripScheduleInTimetable(raptorTimetable, tripAndServiceDate);
    return raptorRequestTransitData.tripScheduleReference(tripSchedule);
  }

  private TripPatternForDates getRaptorTimetableForTripAtStop(
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
}
