package org.opentripplanner.updater.trip;

import java.time.LocalDate;
import org.opentripplanner.transit.model.framework.Result;
import org.opentripplanner.updater.spi.UpdateError;
import org.opentripplanner.updater.trip.model.ParsedExistingTripUpdate;
import org.opentripplanner.updater.trip.model.TripReference;

/**
 * Interface for fuzzy trip matching when exact trip ID lookup fails.
 * <p>
 * Implementations provide different matching strategies:
 * <ul>
 *   <li>{@link LastStopArrivalTimeMatcher} - SIRI-style matching by last stop arrival time</li>
 *   <li>{@link RouteDirectionTimeMatcher} - GTFS-RT-style matching by route/direction/start time</li>
 * </ul>
 * <p>
 * The matcher is called by {@link ExistingTripResolver} when exact trip ID lookup fails
 * and a {@code FuzzyTripMatcher} is configured (controlled by the {@code fuzzyTripMatching}
 * config parameter).
 */
public interface FuzzyTripMatcher {
  /**
   * Attempt to match a trip using fuzzy matching logic.
   *
   * @param tripReference The trip reference with available identification fields
   * @param parsedUpdate The parsed update for an existing trip (provides stop time updates for matching)
   * @param serviceDate The service date to match against
   * @return Result containing the matched trip and pattern, or an error if no match found
   */
  Result<TripAndPattern, UpdateError> match(
    TripReference tripReference,
    ParsedExistingTripUpdate parsedUpdate,
    LocalDate serviceDate
  );
}
