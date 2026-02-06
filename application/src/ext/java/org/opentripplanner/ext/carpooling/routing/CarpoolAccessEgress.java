package org.opentripplanner.ext.carpooling.routing;

import org.opentripplanner.framework.model.TimeAndCost;
import org.opentripplanner.routing.algorithm.raptoradapter.transit.DefaultAccessEgress;
import org.opentripplanner.routing.algorithm.raptoradapter.transit.RoutingAccessEgress;
import org.opentripplanner.street.search.state.State;

public class CarpoolAccessEgress extends DefaultAccessEgress {
  public CarpoolAccessEgress(int stop, int durationInSeconds, int generalizedCost, TimeAndCost penalty, State lastState) {
    super(stop, durationInSeconds, generalizedCost, penalty, lastState);
  }

  public CarpoolAccessEgress(int stop, State lastState) {
    super(stop, lastState);
  }

  protected CarpoolAccessEgress(RoutingAccessEgress other, TimeAndCost penalty) {
    super(other, penalty);
  }
}
