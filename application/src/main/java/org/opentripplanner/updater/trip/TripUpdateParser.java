package org.opentripplanner.updater.trip;

import org.opentripplanner.transit.model.framework.Result;
import org.opentripplanner.updater.spi.UpdateError;
import org.opentripplanner.updater.trip.model.ParsedTripUpdate;

/**
 * Interface for parsing format-specific real-time messages into the common model.
 * <p>
 * Implementations of this interface are responsible for:
 * <ul>
 *   <li>Parsing the format-specific message structure (SIRI-ET or GTFS-RT)</li>
 *   <li>Converting the parsed data into a {@link ParsedTripUpdate}</li>
 *   <li>Validating the input and returning appropriate errors</li>
 * </ul>
 *
 * @param <T> The type of the format-specific update message
 */
public interface TripUpdateParser<T> {
  /**
   * Parse a single format-specific update into the common model.
   *
   * @param update  The format-specific update message
   * @return Result containing either the parsed update or an error
   */
  Result<ParsedTripUpdate, UpdateError> parse(T update);
}
