package org.opentripplanner.raptor.rangeraptor.multicriteria.arrivals;

import org.opentripplanner.raptor.api.model.RaptorTripScheduleStopPosition;
import org.opentripplanner.raptor.spi.RaptorTripSchedule;
import org.opentripplanner.utils.tostring.ToStringBuilder;

public class ArrivalWithBoardingConstraint<T extends RaptorTripSchedule> {

  private final McStopArrival<T> stopArrival;
  private final RaptorTripScheduleStopPosition tripBoarding;

  public ArrivalWithBoardingConstraint(
    McStopArrival<T> stopArrival,
    RaptorTripScheduleStopPosition tripBoarding
  ) {
    this.stopArrival = stopArrival;
    this.tripBoarding = tripBoarding;
  }

  public McStopArrival<T> stopArrival() {
    return stopArrival;
  }

  public RaptorTripScheduleStopPosition tripToBoard() {
    return tripBoarding;
  }

  @Override
  public String toString() {
    return ToStringBuilder.of(ArrivalWithBoardingConstraint.class)
      .addObj("stopArrival", stopArrival)
      .addObj("tripBoarding", tripBoarding)
      .toString();
  }
}
