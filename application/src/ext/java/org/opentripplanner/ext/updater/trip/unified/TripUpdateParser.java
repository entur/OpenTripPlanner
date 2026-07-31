package org.opentripplanner.ext.updater.trip.unified;

import org.opentripplanner.ext.updater.trip.unified.model.command.TripUpdateCommand;
import org.opentripplanner.updater.spi.UpdateException;

/**
 * Interface for parsing format-specific real-time messages into the common model.
 * <p>
 * Implementations of this interface are responsible for:
 * <ul>
 *   <li>Parsing the format-specific message structure (SIRI-ET or GTFS-RT)</li>
 *   <li>Converting the parsed data into a {@link TripUpdateCommand}</li>
 *   <li>Validating the input and returning appropriate errors</li>
 * </ul>
 *
 * @param <T> The type of the format-specific update message
 */
public interface TripUpdateParser<T> {
  /**
   * Parse a single format-specific update into a command of the common model.
   *
   * @param update  The format-specific update message
   * @return the command
   * @throws UpdateException if the update cannot be parsed
   */
  TripUpdateCommand parse(T update) throws UpdateException;
}
