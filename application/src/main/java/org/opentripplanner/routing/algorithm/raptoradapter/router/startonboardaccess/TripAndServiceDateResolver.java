package org.opentripplanner.routing.algorithm.raptoradapter.router.startonboardaccess;

import org.opentripplanner.routing.api.request.TripOnDateReference;
import org.opentripplanner.routing.api.request.TripOnDateReferenceWithTripAndDate;
import org.opentripplanner.routing.api.request.TripOnDateReferenceWithTripOnServiceDateId;
import org.opentripplanner.transit.service.TransitService;

/**
 * Resolves a {@link TripOnDateReference} to a {@link TripAndServiceDate}. Called up-front by both
 * {@link TripScheduleIndexResolver} and {@link TripLocationResolver} callers.
 */
public class TripAndServiceDateResolver {

  private final TransitService transitService;

  public TripAndServiceDateResolver(TransitService transitService) {
    this.transitService = transitService;
  }

  public TripAndServiceDate resolve(TripOnDateReference reference) {
    switch (reference) {
      case TripOnDateReferenceWithTripOnServiceDateId ref:
        var tripOnServiceDate = transitService.getTripOnServiceDate(ref.id());
        if (tripOnServiceDate == null) {
          throw new IllegalArgumentException("TripOnServiceDate not found: " + ref.id());
        }
        return new TripAndServiceDate(
          tripOnServiceDate.getTrip(),
          tripOnServiceDate.getServiceDate()
        );
      case TripOnDateReferenceWithTripAndDate ref:
        var trip = transitService.getTrip(ref.id());
        if (trip == null) {
          throw new IllegalArgumentException("Trip not found: " + ref.id());
        }
        return new TripAndServiceDate(trip, ref.serviceDate());
    }
  }
}
