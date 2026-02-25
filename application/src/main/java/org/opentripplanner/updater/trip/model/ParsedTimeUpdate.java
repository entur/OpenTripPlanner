package org.opentripplanner.updater.trip.model;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * A time update that may need service date resolution.
 * Used during parsing when the service date may not be known yet (e.g., SIRI messages
 * with tripOnServiceDateId but no explicit service date).
 * <p>
 * Implementations:
 * <ul>
 *   <li>{@link TimeUpdate} - Already resolved time (delay-based or absolute)</li>
 *   <li>{@link DeferredTimeUpdate} - Raw ZonedDateTime requiring service date for resolution</li>
 * </ul>
 */
public sealed interface ParsedTimeUpdate permits TimeUpdate, DeferredTimeUpdate {
  /**
   * Resolve this time update to a concrete TimeUpdate.
   *
   * @param serviceDate the resolved service date (must not be null)
   * @param timeZone the timezone for conversion
   * @return the resolved TimeUpdate
   */
  TimeUpdate resolve(LocalDate serviceDate, ZoneId timeZone);
}
