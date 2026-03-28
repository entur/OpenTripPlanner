package org.opentripplanner.updater.trip;

import org.opentripplanner.updater.spi.UpdateException;
import org.opentripplanner.updater.trip.handlers.TripUpdateResult;
import org.opentripplanner.updater.trip.model.ParsedTripUpdate;

/**
 * Interface for applying parsed trip updates to the transit model.
 * <p>
 * This is the common component shared by both SIRI-ET and GTFS-RT updaters.
 * Implementations are responsible for:
 * <ul>
 *   <li>Resolving trip references to actual trips in the transit model</li>
 *   <li>Building or updating TripTimes from the parsed stop time updates</li>
 *   <li>Handling stop pattern modifications (skipped stops, extra calls)</li>
 *   <li>Creating new trips and routes when needed</li>
 *   <li>Applying delay propagation according to the configured options</li>
 * </ul>
 */
public interface TripUpdateApplier {
  /**
   * Apply a parsed trip update to create or update trip times.
   *
   * @param parsedUpdate The format-independent parsed update
   * @return The TripUpdateResult (with RealTimeTripUpdate and warnings)
   * @throws UpdateException if the update cannot be applied
   */
  TripUpdateResult apply(ParsedTripUpdate parsedUpdate) throws UpdateException;
}
