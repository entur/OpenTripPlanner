package org.opentripplanner.updater.trip;

import org.opentripplanner.transit.model.framework.Result;
import org.opentripplanner.updater.spi.UpdateError;
import org.opentripplanner.updater.trip.model.ParsedTripUpdate;
import org.opentripplanner.updater.trip.siri.TripAndPattern;

/**
 * Common interface for trip matching strategies. Used when exact trip IDs are unavailable in
 * real-time feeds, allowing fuzzy matching by alternative identifiers like vehicle ref, route +
 * start time, or stop patterns.
 * <p>
 * Implementations:
 * - {@link SiriTripMatcher}: Matches SIRI trips by vehicle ref, line ref, and stop pattern
 * - GtfsTripMatcher: Matches GTFS-RT trips by route, start time, and direction
 */
public interface TripMatcher {
  /**
   * Attempts to match a trip based on the information in the parsed update.
   *
   * @param parsedUpdate The parsed trip update containing trip reference and stop information
   * @param context The context providing access to transit data and services
   * @return Result containing the matched trip and pattern, or an error if no match found
   */
  Result<TripAndPattern, UpdateError> match(
    ParsedTripUpdate parsedUpdate,
    TripUpdateApplierContext context
  );
}
