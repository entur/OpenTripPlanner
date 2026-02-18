package org.opentripplanner.routing.api.request;

import java.util.Objects;
import javax.annotation.Nullable;
import org.opentripplanner.core.model.id.FeedScopedId;

/**
 * Identifies a position on-board a specific transit trip. Used to start (or potentially end) a trip
 * planning search from on-board a vehicle.
 * <p>
 * The stop is always identified by its {@code stopId}. Optionally, a
 * {@code scheduledDepartureTime} can be provided to disambiguate when the same stop is visited
 * multiple times in the pattern (e.g. ring lines).
 *
 * @param tripOnDateReference    Identifies the trip and service date, either by trip ID +
 *                               service date or by a dated service journey ID.
 * @param stopId                 The stop at which the traveler is considered to be boarding.
 *                               Used together with the trip to identify the exact stop position
 *                               in the pattern.
 * @param scheduledDepartureTime The scheduled departure time at this stop as epoch milliseconds.
 *                               Used for disambiguation when the stop appears more than once in
 *                               the pattern. May be null if not needed.
 */
public record TripLocation(
  TripOnDateReference tripOnDateReference,
  FeedScopedId stopId,
  @Nullable Long scheduledDepartureTime
) {
  public TripLocation {
    Objects.requireNonNull(tripOnDateReference, "tripOnDateReference must be set");
    Objects.requireNonNull(stopId, "stopId must be set");
  }

  /**
   * Create a TripLocation identified by stop ID only.
   */
  public static TripLocation of(TripOnDateReference tripOnDateReference, FeedScopedId stopId) {
    return new TripLocation(tripOnDateReference, stopId, null);
  }

  /**
   * Create a TripLocation identified by stop ID and scheduled departure time (epoch millis).
   */
  public static TripLocation of(
    TripOnDateReference tripOnDateReference,
    FeedScopedId stopId,
    long scheduledDepartureTime
  ) {
    return new TripLocation(tripOnDateReference, stopId, scheduledDepartureTime);
  }
}
