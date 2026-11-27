package org.opentripplanner.raptor.rangeraptor.multicriteria.arrivals.stop;

import org.opentripplanner.raptor.api.model.RaptorAccessEgress;
import org.opentripplanner.raptor.api.view.PatternRideView;
import org.opentripplanner.raptor.spi.RaptorTransfer;
import org.opentripplanner.raptor.spi.RaptorTripSchedule;

public interface McStopArrivalFactory<T extends RaptorTripSchedule> {
  McStopArrival<T> createAccessStopArrival(int departureTime, RaptorAccessEgress accessPath);

  McStopArrival<T> createTransitStopArrival(
    PatternRideView<T, McStopArrival<T>> ride,
    int alightStop,
    int stopArrivalTime,
    int c1
  );

  /**
   * The {@code c2} of the stop arrival that
   * {@link #createTransitStopArrival(PatternRideView, int, int, int)} creates for the given ride.
   * It is the same at every stop of the ride.
   */
  int transitStopArrivalC2(PatternRideView<T, McStopArrival<T>> ride);

  McStopArrival<T> createTransferStopArrival(
    McStopArrival<T> previous,
    RaptorTransfer transfer,
    int arrivalTime
  );
}
