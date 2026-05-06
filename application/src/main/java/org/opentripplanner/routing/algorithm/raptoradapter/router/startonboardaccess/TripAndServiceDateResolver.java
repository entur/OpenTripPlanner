package org.opentripplanner.routing.algorithm.raptoradapter.router.startonboardaccess;

import org.opentripplanner.routing.api.request.TripOnDateReference;
import org.opentripplanner.transit.service.TransitService;

/**
 * Resolves a {@link TripOnDateReference} to a {@link TripAndServiceDate}. Called up-front by both
 * {@link StartOnBoardAccessResolver} and {@link StartOnBoardBoardingTimeResolver} callers.
 */
public class TripAndServiceDateResolver {

  private final TransitService transitService;

  public TripAndServiceDateResolver(TransitService transitService) {
    this.transitService = transitService;
  }

  public TripAndServiceDate resolve(TripOnDateReference reference) {
    if (reference.tripOnServiceDateId() != null) {
      var tripOnServiceDate = transitService.getTripOnServiceDate(reference.tripOnServiceDateId());
      if (tripOnServiceDate == null) {
        throw new IllegalArgumentException(
          "TripOnServiceDate not found: " + reference.tripOnServiceDateId()
        );
      }
      return new TripAndServiceDate(
        tripOnServiceDate.getTrip(),
        tripOnServiceDate.getServiceDate()
      );
    } else if (reference.tripIdOnServiceDate() != null) {
      var tripIdAndDate = reference.tripIdOnServiceDate();
      var trip = transitService.getTrip(tripIdAndDate.tripId());
      if (trip == null) {
        throw new IllegalArgumentException("Trip not found: " + tripIdAndDate.tripId());
      }
      return new TripAndServiceDate(trip, tripIdAndDate.serviceDate());
    }

    throw new IllegalArgumentException(
      "Either tripOnServiceDateId or tripIdOnServiceDate must be set on TripOnDateReference"
    );
  }
}
