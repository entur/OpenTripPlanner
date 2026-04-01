package org.opentripplanner.raptor.rangeraptor.multicriteria.arrivals;

import org.opentripplanner.raptor.api.model.RaptorTripScheduleStopPosition;
import org.opentripplanner.raptor.spi.RaptorTripSchedule;
import org.opentripplanner.utils.tostring.ToStringBuilder;

public class ArrivalWithBoardingConstraint<T extends RaptorTripSchedule> {

  private final McStopArrival<T> stopArrival;
  private final RaptorTripScheduleStopPosition boardingConstraint;

  public ArrivalWithBoardingConstraint(
    McStopArrival<T> stopArrival,
    RaptorTripScheduleStopPosition boardingConstraint
  ) {
    this.stopArrival = stopArrival;
    this.boardingConstraint = boardingConstraint;
  }

  public McStopArrival<T> stopArrival() {
    return stopArrival;
  }

  public RaptorTripScheduleStopPosition tripToBoard() {
    return boardingConstraint;
  }

  @Override
  public String toString() {
    return ToStringBuilder.of(ArrivalWithBoardingConstraint.class)
      .addObj("stopArrival", stopArrival)
      .addObj("boardingConstraint", boardingConstraint)
      .toString();
  }
}
