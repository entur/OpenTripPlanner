package org.opentripplanner.updater.trip;

import org.opentripplanner.transit.model.framework.Result;
import org.opentripplanner.transit.model.timetable.RealTimeTripUpdate;
import org.opentripplanner.updater.spi.UpdateError;
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
   * @param context      Application context containing snapshot manager and other resources
   * @return Result containing the RealTimeTripUpdate for the snapshot manager, or an error
   */
  Result<RealTimeTripUpdate, UpdateError> apply(
    ParsedTripUpdate parsedUpdate,
    TripUpdateApplierContext context
  );
}
