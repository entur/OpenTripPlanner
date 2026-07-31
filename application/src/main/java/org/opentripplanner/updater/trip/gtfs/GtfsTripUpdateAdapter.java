package org.opentripplanner.updater.trip.gtfs;

import org.opentripplanner.transit.repository.TimetableRepository;

/**
 * Application-scoped factory for GTFS-RT trip update processing. Holds stable,
 * application-lifetime state and produces a per-task {@link GtfsTripUpdateHandler} via
 * {@link #forUpdate(TimetableRepository)}.
 * <p>
 * This abstraction allows switching between the legacy implementation
 * ({@link GtfsRealTimeTripUpdateAdapter}), the new format-independent implementation
 * ({@link GtfsNewTripUpdateAdapter}) and the shadow-comparison mode
 * ({@link ShadowGtfsTripUpdateAdapter}).
 */
public interface GtfsTripUpdateAdapter {
  /**
   * Create an update-scoped task for applying GTFS-RT trip updates. All pattern and trip lookups
   * within the task see in-progress real-time additions in the given buffer.
   */
  GtfsTripUpdateHandler forUpdate(TimetableRepository buffer);
}
