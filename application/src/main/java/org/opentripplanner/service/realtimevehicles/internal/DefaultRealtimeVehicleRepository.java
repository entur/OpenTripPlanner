package org.opentripplanner.service.realtimevehicles.internal;

import static org.opentripplanner.transit.model.timetable.OccupancyStatus.NO_DATA_AVAILABLE;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Multimap;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.service.realtimevehicles.RealtimeVehicleRepository;
import org.opentripplanner.service.realtimevehicles.model.RealtimeVehicle;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.timetable.OccupancyStatus;

@Singleton
public class DefaultRealtimeVehicleRepository implements RealtimeVehicleRepository {

  /**
   * This multimap is immutable and therefore thread-safe. It is updated using the copy-on-write
   * pattern so data races are avoided. This is re-enforced with the variable being volatile.
   */
  private volatile ImmutableListMultimap<TripPattern, RealtimeVehicle> vehicles =
    ImmutableListMultimap.of();

  @Inject
  public DefaultRealtimeVehicleRepository() {}

  @Override
  public void setRealtimeVehiclesForFeed(
    String feedId,
    Multimap<TripPattern, RealtimeVehicle> updates
  ) {
    Multimap<TripPattern, RealtimeVehicle> temp = ArrayListMultimap.create();
    temp.putAll(vehicles);
    // remove all previous updates for this specific feed id
    vehicles
      .keys()
      .stream()
      .filter(p -> p.getFeedId().equals(feedId))
      .forEach(temp::removeAll);
    // transform keys and put all fresh updates into map
    updates.forEach((pattern, vehicle) -> {
      if (pattern.getOriginalTripPattern() != null) {
        pattern = pattern.getOriginalTripPattern();
      }
      temp.put(pattern, vehicle);
    });

    vehicles = ImmutableListMultimap.copyOf(temp);
  }

  @Override
  public List<RealtimeVehicle> getRealtimeVehicles(TripPattern pattern) {
    // the list is made immutable during insertion, so we can safely return them
    return vehicles.get(pattern);
  }

  @Override
  public OccupancyStatus getOccupancyStatus(FeedScopedId tripId, TripPattern pattern) {
    return vehicles
      .get(pattern)
      .stream()
      .filter(vehicle -> tripId.equals(vehicle.trip().getId()))
      .max(Comparator.comparing(vehicle -> vehicle.time().orElse(Instant.MIN)))
      .flatMap(RealtimeVehicle::occupancyStatus)
      .orElse(NO_DATA_AVAILABLE);
  }
}
