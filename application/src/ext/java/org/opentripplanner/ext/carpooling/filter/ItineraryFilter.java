package org.opentripplanner.ext.carpooling.filter;

import java.time.Duration;
import org.opentripplanner.model.plan.Itinerary;

public interface ItineraryFilter {
  boolean accepts(Itinerary itinerary, CarpoolingRequest request, Duration searchWindow);
}
