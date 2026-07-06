package org.opentripplanner.service.realtimevehicles;

import com.google.common.collect.Multimap;
import java.util.List;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.service.realtimevehicles.model.RealtimeVehicle;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.timetable.OccupancyStatus;

/**
 * Stores the realtime vehicles. There is one instance for the whole application: it is written
 * by the vehicle-position updater on the graph writer thread and read concurrently by request
 * threads, usually through the request-scoped {@link RealtimeVehicleService}.
 */
public interface RealtimeVehicleRepository {
  /**
   * Stores all realtime vehicles for a given {@code feedId}. Each vehicle should be keyed by the
   * pattern of its trip in the scheduled data — a stable key that does not depend on real-time
   * modifications of the trip's pattern. Lookups with a realtime pattern are resolved to the
   * stored keys by the {@link RealtimeVehicleService}.
   * <p>
   * Before storing the new vehicles, it removes the previous updates for the given {@code feedId}.
   */
  void setRealtimeVehiclesForFeed(String feedId, Multimap<TripPattern, RealtimeVehicle> updates);

  /**
   * Get the vehicles stored for the given pattern key. This is a raw lookup: the pattern must be
   * the exact key used when storing, the pattern of the trip in the scheduled data. Use
   * {@link RealtimeVehicleService#getRealtimeVehicles(TripPattern)} to also resolve patterns
   * created by real-time updates.
   */
  List<RealtimeVehicle> getRealtimeVehicles(TripPattern pattern);

  /**
   * Get the latest occupancy status for a certain trip on the given pattern key. As for
   * {@link #getRealtimeVehicles(TripPattern)} the pattern must be the exact key used when
   * storing.
   */
  OccupancyStatus getOccupancyStatus(FeedScopedId tripId, TripPattern pattern);
}
