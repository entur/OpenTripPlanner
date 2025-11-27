package org.opentripplanner.model.plan;

import java.util.List;
import org.opentripplanner.framework.model.Cost;

public class TestItinerary {

  public static ItineraryBuilder of(List<Leg> legs) {
    return Itinerary.ofScheduledTransit(legs).withGeneralizedCost(Cost.ZERO);
  }

  public static ItineraryBuilder of(Leg leg) {
    return of(List.of(leg));
  }
}
