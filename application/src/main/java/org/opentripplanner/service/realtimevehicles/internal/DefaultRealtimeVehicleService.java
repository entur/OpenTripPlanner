package org.opentripplanner.service.realtimevehicles.internal;

import java.util.List;
import org.opentripplanner.service.realtimevehicles.RealtimeVehicleRepository;
import org.opentripplanner.service.realtimevehicles.RealtimeVehicleService;
import org.opentripplanner.service.realtimevehicles.model.RealtimeVehicle;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.timetable.OccupancyStatus;
import org.opentripplanner.transit.model.timetable.Trip;
import org.opentripplanner.transit.service.TransitService;

/**
 * A request-scoped view over the {@link RealtimeVehicleRepository}. Vehicles are stored in the
 * repository keyed by the pattern of their trip in the scheduled data; this view resolves
 * lookups with patterns created by real-time updates through the trips currently running on
 * them, using the transit service — and thereby the timetable snapshot — of the request.
 * <p>
 * A new instance should be created for each request, with the request's {@link TransitService},
 * so that the whole request sees one consistent timetable snapshot.
 */
public class DefaultRealtimeVehicleService implements RealtimeVehicleService {

  private final RealtimeVehicleRepository repository;
  private final TransitService transitService;

  public DefaultRealtimeVehicleService(
    RealtimeVehicleRepository repository,
    TransitService transitService
  ) {
    this.repository = repository;
    this.transitService = transitService;
  }

  @Override
  public List<RealtimeVehicle> getRealtimeVehicles(TripPattern pattern) {
    if (pattern.isRealTimeTripPattern()) {
      return transitService
        .listTrips(pattern)
        .stream()
        .map(transitService::findPattern)
        .distinct()
        .flatMap(scheduledPattern -> repository.getRealtimeVehicles(scheduledPattern).stream())
        .toList();
    }
    return repository.getRealtimeVehicles(pattern);
  }

  @Override
  public OccupancyStatus getVehicleOccupancyStatus(Trip trip) {
    return repository.getOccupancyStatus(trip.getId(), transitService.findPattern(trip));
  }
}
