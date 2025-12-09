package org.opentripplanner.transit.service;

import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.opentripplanner.model.TimetableSnapshot;
import org.opentripplanner.transit.model.network.Replacement;
import org.opentripplanner.transit.model.network.Route;
import org.opentripplanner.transit.model.timetable.Trip;
import org.opentripplanner.transit.model.timetable.TripOnServiceDate;

public class ReplacementHelper {

  private final TransitService transitService;
  private final TimetableRepository timetableRepository;

  @Nullable
  private final TimetableSnapshot timetableSnapshot;

  public ReplacementHelper(
    TransitService transitService,
    TimetableRepository timetableRepository,
    @Nullable TimetableSnapshot timetableSnapshot
  ) {
    this.transitService = transitService;
    this.timetableRepository = timetableRepository;
    this.timetableSnapshot = timetableSnapshot;
  }

  public boolean isReplacementRoute(Route route) {
    if (route.getGtfsType() != null && route.getGtfsType() == 714) {
      return true;
    }
    return route.getNetexSubmode().toString().toLowerCase().contains("replacement");
  }

  public boolean replacementRoutesExist(Route route) {
    for (var tripOnServiceDate : transitService.listTripsOnServiceDate()) {
      if (tripOnServiceDate.getTrip().getRoute().getId().equals(route.getId())) {
        var id = tripOnServiceDate.getId();
        return (
          !timetableRepository.getReplacedByTripOnServiceDate(id).isEmpty() ||
          (timetableSnapshot != null &&
            !timetableSnapshot.getReplacedByTripOnServiceDate(id).isEmpty())
        );
      }
    }
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
    for (var tripOnServiceDate : transitService.listTripsOnServiceDate()) {
      if (tripOnServiceDate.getTrip().getId().equals(trip.getId())) {
        var id = tripOnServiceDate.getId();
        return (
          !timetableRepository.getReplacedByTripOnServiceDate(id).isEmpty() ||
          (timetableSnapshot != null &&
            !timetableSnapshot.getReplacedByTripOnServiceDate(id).isEmpty())
        );
      }
    }
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
    var id = tripOnServiceDate.getId();
    var replacedBy = timetableRepository.getReplacedByTripOnServiceDate(id);
    if (timetableSnapshot != null) {
      return Stream.concat(
        replacedBy.stream(),
        timetableSnapshot.getReplacedByTripOnServiceDate(id).stream()
      ).toList();
    }
    return replacedBy;
  }
}
