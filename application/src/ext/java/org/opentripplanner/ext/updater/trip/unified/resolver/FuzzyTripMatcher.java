package org.opentripplanner.ext.updater.trip.unified.resolver;

import java.time.LocalDate;
import java.util.Optional;
import org.opentripplanner.ext.updater.trip.unified.model.command.TripReference;
import org.opentripplanner.ext.updater.trip.unified.model.command.TripUpdateCommand;
import org.opentripplanner.updater.spi.UpdateException;

/**
 * Identifies the trip an update is about when the identifiers it carries have not identified one -
 * fuzzy trip matching, tried after exact resolution fails and enabled by the
 * {@code fuzzyTripMatching} config parameter.
 * <p>
 * The two formats disagree about what a failed match is, and the contract states both answers:
 * <ul>
 *   <li>{@link org.opentripplanner.ext.updater.trip.unified.gtfs.GtfsTripMatcher GtfsTripMatcher}
 *   matches by route, direction and start time, and a failed match is a <em>non-answer</em>: the
 *   matcher returns empty and the caller falls back on whatever it would have done without a
 *   matcher. The one verdict it owns is that a message naming its trip neither by id nor by a
 *   tuple that matches anything has identified nothing at all.</li>
 *   <li>{@link org.opentripplanner.ext.updater.trip.unified.siri.SiriTripMatcher SiriTripMatcher}
 *   matches by the stops and times of the journey's calls. A command that carries calls gets a
 *   verdict when the match fails - it throws NO_VALID_STOPS, INVALID_DEPARTURE_TIME,
 *   NO_FUZZY_TRIP_MATCH or MULTIPLE_FUZZY_TRIP_MATCHES, which the caller propagates - but a
 *   command shape that carries none (a removal, a duplication) is declined with empty, since
 *   there is nothing to match on and so nothing to have a verdict about.</li>
 * </ul>
 * {@link NoOpFuzzyTripMatcher} - matching disabled - always returns empty, so "disabled" and
 * "found nothing" read the same to a caller: report the failure of the lookup you were doing.
 */
public interface FuzzyTripMatcher {
  /**
   * Attempt to identify the trip the command is about.
   *
   * @param tripReference The trip reference with the identification fields the feed reported
   * @param command The whole command, because a format may identify a trip by what its calls say
   * @param serviceDate The service date the update will be applied on
   * @return the matched trip and the pattern it runs on, or empty when this matcher has no verdict
   *         of its own - the caller then falls back on whatever it would have done without a
   *         matcher, be that rejecting the update or trying its next lookup
   * @throws UpdateException when the matcher's own verdict is the update's verdict
   */
  Optional<TripAndPattern> match(
    TripReference tripReference,
    TripUpdateCommand command,
    LocalDate serviceDate
  ) throws UpdateException;
}
