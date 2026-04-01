package org.opentripplanner.raptor.rangeraptor.internalapi;

import org.opentripplanner.raptor.api.model.RaptorTripScheduleStopPosition;
import org.opentripplanner.raptor.api.view.ArrivalView;
import org.opentripplanner.raptor.spi.RaptorTripSchedule;

public class OnTripAccessArrival<T extends RaptorTripSchedule> {

  private final ArrivalView<T> accessStopArrival;
  private final RaptorTripScheduleStopPosition boardingConstrant;

  public OnTripAccessArrival(
    ArrivalView<T> accessStopArrival,
    RaptorTripScheduleStopPosition boardingConstrant
  ) {
    this.accessStopArrival = accessStopArrival;
    this.boardingConstrant = boardingConstrant;
  }

  public ArrivalView<T> accessStopArrival() {
    return accessStopArrival;
  }

  public RaptorTripScheduleStopPosition boardingConstrant() {
    return boardingConstrant;
  }
}
