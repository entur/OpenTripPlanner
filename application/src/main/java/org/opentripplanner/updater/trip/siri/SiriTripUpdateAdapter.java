package org.opentripplanner.updater.trip.siri;

import org.opentripplanner.transit.repository.MutableTimetableSnapshot;

/**
 * Application-scoped factory for SIRI-ET estimated timetable processing. Holds stable,
 * application-lifetime state and produces a per-task {@link SiriTripUpdateHandler} via
 * {@link #forUpdate(MutableTimetableSnapshot)}.
 * <p>
 * This abstraction allows switching between the legacy implementation
 * ({@link SiriRealTimeTripUpdateAdapter}), the new format-independent implementation
 * ({@link SiriNewTripUpdateAdapter}) and the shadow-comparison mode
 * ({@link ShadowSiriTripUpdateAdapter}).
 */
public interface SiriTripUpdateAdapter {
  /**
   * Create an update-scoped task for applying SIRI-ET estimated timetables. All pattern and trip
   * lookups within the task see in-progress real-time additions in the given buffer.
   */
  SiriTripUpdateHandler forUpdate(MutableTimetableSnapshot buffer);
}
