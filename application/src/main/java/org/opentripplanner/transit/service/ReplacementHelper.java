package org.opentripplanner.transit.service;

import java.util.List;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.opentripplanner.transit.model.basic.SubMode;
import org.opentripplanner.transit.model.network.Replacement;
import org.opentripplanner.transit.model.network.Route;
import org.opentripplanner.transit.model.timetable.TimetableSnapshot;
import org.opentripplanner.transit.model.timetable.Trip;
import org.opentripplanner.transit.model.timetable.TripOnServiceDate;

public class ReplacementHelper {

  // Specially recognized standard GTFS extended route types
  private static final int REPLACEMENT_RAIL_SERVICE = 110;
  private static final int RAIL_REPLACEMENT_BUS_SERVICE = 714;
  private static final List<Integer> REPLACEMENT_EXTENDED_TYPES = List.of(
    REPLACEMENT_RAIL_SERVICE,
    RAIL_REPLACEMENT_BUS_SERVICE
  );

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

  public Replacement getReplacement(TripOnServiceDate tripOnServiceDate) {
    var replacementFor = tripOnServiceDate.getReplacementFor();
    return new Replacement(
      !replacementFor.isEmpty() || isReplacementTrip(tripOnServiceDate.getTrip()),
      replacementFor
    );
  }

  private boolean submodeIsReplacement(SubMode submode) {
    return submode.toString().toLowerCase().contains("replacement");
  }

  private boolean isReplacementGtfsType(Route route) {
    var type = route.getGtfsType();
    return type != null && REPLACEMENT_EXTENDED_TYPES.contains(type);
  }

  public boolean isReplacementRoute(Route route) {
    return isReplacementGtfsType(route) || submodeIsReplacement(route.getNetexSubmode());
  }

  public boolean isReplacementTrip(Trip trip) {
    return isReplacementGtfsType(trip.getRoute()) || submodeIsReplacement(trip.getNetexSubMode());
  }

  private boolean haveReplacedByTripOnServiceDate(TripOnServiceDate tripOnServiceDate) {
    var id = tripOnServiceDate.getId();
    return (
      !timetableRepository.getReplacedByTripOnServiceDate(id).isEmpty() ||
      (timetableSnapshot != null && timetableSnapshot.getReplacedByTripOnServiceDate(id).isEmpty())
    );
  }

  public boolean replacementsExist(Route route) {
    return transitService
      .listTripsOnServiceDate()
      .stream()
      .anyMatch(
        tripOnServiceDate ->
          tripOnServiceDate.getTrip().getRoute().getId().equals(route.getId()) &&
          haveReplacedByTripOnServiceDate(tripOnServiceDate)
      );
  }

  public boolean replacementsExist(Trip trip) {
    return transitService
      .listTripsOnServiceDate()
      .stream()
      .anyMatch(
        tripOnServiceDate ->
          tripOnServiceDate.getTrip().getId().equals(trip.getId()) &&
          haveReplacedByTripOnServiceDate(tripOnServiceDate)
      );
  }
}
