package org.opentripplanner.raptorlegacy._data.transit;

import org.opentripplanner.raptor.spi.RaptorTripScheduleSearch;
import org.opentripplanner.raptor.spi.SearchDirection;
import org.opentripplanner.routing.algorithm.raptoradapter.transit.request.TripScheduleSearchFactory;
import org.opentripplanner.routing.algorithm.raptoradapter.transit.request.TripSearchTimetable;

/**
 *
 * @deprecated This was earlier part of Raptor and should not be used outside the Raptor module. Use
 *             the OTP model entities instead.
 */
@Deprecated
public class TestTripSearchTimetable implements TripSearchTimetable<TestTripSchedule> {

  private final TestTripSchedule[] trips;
  private final boolean arrivalsInTripOrder;
  private final boolean departuresInTripOrder;

  public TestTripSearchTimetable(TestRoute route) {
    int nTrips = route.timetable().numberOfTripSchedules();
    this.trips = new TestTripSchedule[nTrips];
    boolean arrivalsInTripOrder = true;
    boolean departuresInTripOrder = true;

    for (int i = 0; i < nTrips; ++i) {
      trips[i] = route.getTripSchedule(i);

      if (i > 0) {
        int nStops = Math.min(trips[i - 1].numberOfStopTimes(), trips[i].numberOfStopTimes());
        for (int s = 0; s < nStops; ++s) {
          arrivalsInTripOrder &= trips[i - 1].arrival(s) <= trips[i].arrival(s);
          departuresInTripOrder &= trips[i - 1].departure(s) <= trips[i].departure(s);
        }
      }
    }
    this.arrivalsInTripOrder = arrivalsInTripOrder;
    this.departuresInTripOrder = departuresInTripOrder;
  }

  @Override
  public TestTripSchedule getTripSchedule(int index) {
    return trips[index];
  }

  @Override
  public int numberOfTripSchedules() {
    return trips.length;
  }

  @Override
  public int arrivalTime(int stopPositionInPattern, int tripIndex) {
    return trips[tripIndex].arrival(stopPositionInPattern);
  }

  @Override
  public int departureTime(int stopPositionInPattern, int tripIndex) {
    return trips[tripIndex].departure(stopPositionInPattern);
  }

  @Override
  public boolean departuresInTripOrder() {
    return departuresInTripOrder;
  }

  @Override
  public boolean arrivalsInTripOrder() {
    return arrivalsInTripOrder;
  }

  @Override
  public RaptorTripScheduleSearch<TestTripSchedule> tripSearch(SearchDirection direction) {
    return TripScheduleSearchFactory.create(direction, this);
  }
}
