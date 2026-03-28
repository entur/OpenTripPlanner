package org.opentripplanner.updater.trip;

import org.opentripplanner.updater.spi.UpdateException;
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
   * @return The parsed update
   * @throws UpdateException if the update cannot be parsed
   */
  ParsedTripUpdate parse(T update) throws UpdateException;
}
