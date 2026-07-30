package org.opentripplanner.updater.trip.model;

import javax.annotation.Nullable;
import org.opentripplanner.transit.model.network.StopPattern;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.timetable.Trip;

/**
 * Finds the real-time pattern a modified stop pattern maps to, creating it if this is the first
 * trip to run it. Injects {@code TripPatternCache#getOrCreateTripPattern} into the resolved
 * updates, which build the modified stop pattern but do not own the patterns shared between trips.
 */
@FunctionalInterface
public interface ModifiedPatternLookup {
  /**
   * @param stopPattern      the stop pattern the trip runs today
   * @param trip             the trip whose route, mode and submode a newly created pattern copies
   * @param scheduledPattern the pattern the trip is scheduled to run, returned as-is when the
   *                         modified stop pattern turns out to equal the scheduled one
   */
  TripPattern findOrCreate(
    StopPattern stopPattern,
    Trip trip,
    @Nullable TripPattern scheduledPattern
  );
}
