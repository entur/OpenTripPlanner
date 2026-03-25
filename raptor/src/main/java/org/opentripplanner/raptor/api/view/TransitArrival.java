package org.opentripplanner.raptor.api.view;

import org.opentripplanner.raptor.api.model.RaptorTripScheduleStopPosition;
import org.opentripplanner.raptor.spi.RaptorTripSchedule;

/**
 * @param <T> The TripSchedule type defined by the user of the raptor API.
 */
public interface TransitArrival<T extends RaptorTripSchedule> {
  static <T extends RaptorTripSchedule> TransitArrival<T> create(
    final T trip,
    final int stop,
    final int time
  ) {
    return new TransitArrival<>() {
      @Override
      public T trip() {
        return trip;
      }

      @Override
      public int stop() {
        return stop;
      }

      @Override
      public int arrivalTime() {
        return time;
      }
    };
  }

  T trip();

  int stop();

  int arrivalTime();

  default RaptorTripScheduleStopPosition tripArrival() {
    var trip = trip();
    int stopPosInPattern = trip.findArrivalStopPosition(arrivalTime(), stop());
    return new RaptorTripScheduleStopPosition(trip, stopPosInPattern);
  }
}
