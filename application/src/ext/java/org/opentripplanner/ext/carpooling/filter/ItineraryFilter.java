package org.opentripplanner.ext.carpooling.filter;

import java.time.Duration;
import org.opentripplanner.model.plan.Itinerary;

/**
 * Post-filter applied to fully-routed carpool itineraries.
 * <p>
 * Unlike {@link TripFilter}, which screens raw trips before routing, implementations receive
 * complete {@link Itinerary} objects and can inspect actual departure and arrival times.
 */
public interface ItineraryFilter {
  /**
   * Returns {@code true} if the itinerary satisfies this filter's criteria.
   * <p>
   * Post-filters apply tight enforcement after routing. The search window has already been used by
   * the pre-filter ({@link TripFilter}) to limit which raw trips were routed, so implementations
   * here check exact itinerary times, not the window.
   *
   * @param itinerary    The routed itinerary to evaluate
   * @param request      The passenger's journey preferences
   * @param searchWindow For depart-after: passed through for context; the pre-filter has already
   *                     enforced {@code trip.startTime <= T + searchWindow}, so post-filters only
   *                     need to enforce {@code itinerary.startTime >= T} (see
   *                     {@link DepartAfterFilter}).
   *                     For arrive-by: not used — any itinerary arriving at or before T is
   *                     accepted ({@code (-∞, T]}), enforced by {@link ArriveByFilter}.
   * @return true if the itinerary passes the filter, false otherwise
   */
  boolean accepts(Itinerary itinerary, CarpoolingRequest request, Duration searchWindow);
}
