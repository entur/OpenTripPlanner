package org.opentripplanner.routing.api.request;

import java.time.LocalDateTime;
import javax.annotation.Nullable;
import org.opentripplanner.core.model.id.FeedScopedId;

/**
 * Identifies a position on-board a specific transit trip. Used to start (or potentially end) a trip
 * planning search from on-board a vehicle.
 *
 * @param tripOnDateReference Identifies the trip and service date, either by trip ID + service date
 *                            or by a dated service journey ID.
 * @param stopId              The stop at which the traveler is considered to be boarding. Used
 *                            together with the trip to identify the exact stop position in the
 *                            pattern.
 * @param scheduledDepartureTime Disambiguates the stop position in the pattern in the case of ring
 *                               lines where the same stop is visited more than once. If provided,
 *                               the stop whose scheduled departure time matches is used.
 */
public record TripLocation(
  TripOnDateReference tripOnDateReference,
  FeedScopedId stopId,
  @Nullable LocalDateTime scheduledDepartureTime
) {
  public TripLocation {
    if (tripOnDateReference == null) {
      throw new IllegalArgumentException("tripOnDateReference must be set");
    }
    if (stopId == null) {
      throw new IllegalArgumentException("stopId must be set");
    }
  }
}
