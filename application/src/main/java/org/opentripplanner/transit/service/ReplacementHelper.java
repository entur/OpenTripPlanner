package org.opentripplanner.transit.service;

import java.util.List;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.opentripplanner.transit.model.basic.SubMode;
import org.opentripplanner.transit.model.network.ReplacedByRelation;
import org.opentripplanner.transit.model.network.ReplacementForRelation;
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

  public Iterable<ReplacedByRelation> getReplacedBy(TripOnServiceDate tripOnServiceDate) {
    var id = tripOnServiceDate.getId();
    var replacedBy = timetableRepository.getReplacedByTripOnServiceDate(id);
    Stream<TripOnServiceDate> tripsOnServiceDate;
    if (timetableSnapshot != null) {
      tripsOnServiceDate = Stream.concat(
        replacedBy.stream(),
        timetableSnapshot.getReplacedByTripOnServiceDate(id).stream()
      );
    } else {
      tripsOnServiceDate = replacedBy.stream();
    }
    return tripsOnServiceDate.map(ReplacedByRelation::new).toList();
  }

  public Iterable<ReplacementForRelation> getReplacementFor(TripOnServiceDate tripOnServiceDate) {
    return tripOnServiceDate.getReplacementFor().stream().map(ReplacementForRelation::new).toList();
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

  public boolean isReplacementTripOnServiceDate(TripOnServiceDate tripOnServiceDate) {
    return (
      !tripOnServiceDate.getReplacementFor().isEmpty() ||
      isReplacementTrip(tripOnServiceDate.getTrip())
    );
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
