package org.opentripplanner.updater.trip.resolver;

import java.time.LocalDate;
import org.opentripplanner.updater.spi.UpdateException;
import org.opentripplanner.updater.trip.model.command.ExistingTripCommand;
import org.opentripplanner.updater.trip.model.command.TripReference;

/**
 * Interface for fuzzy trip matching when exact trip ID lookup fails.
 * <p>
 * Implementations provide different matching strategies:
 * <ul>
 *   <li>{@link org.opentripplanner.updater.trip.siri.SiriTripMatcher SiriTripMatcher} - SIRI-style matching by last stop arrival time</li>
 *   <li>{@link org.opentripplanner.updater.trip.gtfs.GtfsTripMatcher GtfsTripMatcher} - GTFS-RT-style matching by route/direction/start time</li>
 * </ul>
 * <p>
 * The matcher is called by {@link org.opentripplanner.updater.trip.factory.ExistingTripChangeFactory ExistingTripChangeFactory} when exact trip ID lookup fails
 * and a {@code FuzzyTripMatcher} is configured (controlled by the {@code fuzzyTripMatching}
 * config parameter).
 */
public interface FuzzyTripMatcher {
  /**
   * Attempt to match a trip using fuzzy matching logic.
   *
   * @param tripReference The trip reference with available identification fields
   * @param command The command for an existing trip (provides stop time updates for matching)
   * @param serviceDate The service date to match against
   * @return The matched trip and pattern
   * @throws UpdateException if no match is found
   */
  TripAndPattern match(
    TripReference tripReference,
    ExistingTripCommand command,
    LocalDate serviceDate
  ) throws UpdateException;

  /**
   * Whether this matcher actually attempts a fuzzy match. The {@link NoOpFuzzyTripMatcher} returns
   * {@code false} so callers can preserve the original exact-match error instead of reporting a
   * fuzzy-match failure.
   */
  default boolean isEnabled() {
    return true;
  }
}
