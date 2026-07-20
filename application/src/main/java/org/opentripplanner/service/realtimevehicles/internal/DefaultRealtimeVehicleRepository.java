package org.opentripplanner.service.realtimevehicles.internal;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Multimap;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.List;
import org.opentripplanner.service.realtimevehicles.RealtimeVehicleRepository;
import org.opentripplanner.service.realtimevehicles.RealtimeVehicleRepositorySnapshot;
import org.opentripplanner.service.realtimevehicles.model.RealtimeVehicle;
import org.opentripplanner.transit.model.network.TripPattern;

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
  public RealtimeVehicleRepositorySnapshot createSnapshot() {
    // the multimap is immutable, so the snapshot can simply wrap the current value
    return new Snapshot(vehicles);
  }

  /** Immutable snapshot of the repository state. */
  private static class Snapshot implements RealtimeVehicleRepositorySnapshot {

    private final ImmutableListMultimap<TripPattern, RealtimeVehicle> vehicles;

    private Snapshot(ImmutableListMultimap<TripPattern, RealtimeVehicle> vehicles) {
      this.vehicles = vehicles;
    }

    @Override
    public List<RealtimeVehicle> getRealtimeVehicles(TripPattern pattern) {
      return vehicles.get(pattern);
    }
  }
}
