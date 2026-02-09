package org.opentripplanner.ext.carpooling.routing;

import org.opentripplanner.framework.model.TimeAndCost;
import org.opentripplanner.routing.algorithm.raptoradapter.transit.DefaultAccessEgress;
import org.opentripplanner.routing.algorithm.raptoradapter.transit.RoutingAccessEgress;
import org.opentripplanner.street.search.state.State;
import org.opentripplanner.utils.time.TimeUtils;

public class CarpoolAccessEgress extends DefaultAccessEgress {

  private final int startOfTrip;
  private final int endOfTrip;

  public CarpoolAccessEgress(int stop, State lastState, int startOfTrip, int endOfTrip) {
    super(stop, lastState);
    this.startOfTrip = startOfTrip;
    this.endOfTrip = endOfTrip;
  }

  @Override
  public int earliestDepartureTime(int requestedDepartureTime) {
    return startOfTrip;
  }

  @Override
  public int latestArrivalTime(int requestedArrivalTime) {
    return endOfTrip;
  }


  public int getStartOfTrip() {return startOfTrip;}
  public int getEndOfTrip() {return endOfTrip;}
}
