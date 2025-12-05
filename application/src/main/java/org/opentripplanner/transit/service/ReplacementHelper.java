package org.opentripplanner.transit.service;

import java.util.Collections;
import org.opentripplanner.transit.model.network.Replacement;
import org.opentripplanner.transit.model.network.Route;
import org.opentripplanner.transit.model.timetable.Trip;
import org.opentripplanner.transit.model.timetable.TripOnServiceDate;

public class ReplacementHelper {

  private TransitService transitService;

  public ReplacementHelper(TransitService transitService) {
    this.transitService = transitService;
  }

  public boolean isReplacementRoute(Route route) {
    if (route.getGtfsType() != null && route.getGtfsType() == 714) {
      return true;
    }
    return route.getNetexSubmode().toString().toLowerCase().contains("replacement");
  }

  public boolean replacementRoutesExist(Route route) {
    return false;
  }

  public boolean isReplacementTrip(Trip trip) {
    var route = trip.getRoute();
    if (route.getGtfsType() != null && route.getGtfsType() == 714) {
      return true;
    }
    return trip.getNetexSubMode().toString().toLowerCase().contains("replacement");
  }

  public boolean replacementTripsExist(Trip trip) {
    return false;
  }

  public Replacement getReplacement(TripOnServiceDate tripOnServiceDate) {
    var replacementFor = tripOnServiceDate.getReplacementFor();
    return new Replacement(
      !replacementFor.isEmpty() || isReplacementTrip(tripOnServiceDate.getTrip()),
      replacementFor
    );
  }

  public Iterable<TripOnServiceDate> getReplacedBy(TripOnServiceDate tripOnServiceDate) {
    return Collections.emptyList();
  }
}
