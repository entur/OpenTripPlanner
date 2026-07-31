package org.opentripplanner.ext.updater.trip.unified.resolver;

import java.time.LocalDate;
import java.util.Optional;
import org.opentripplanner.ext.updater.trip.unified.model.command.TripReference;
import org.opentripplanner.ext.updater.trip.unified.model.command.TripUpdateCommand;

/**
 * The matcher of a deployment that identifies trips only by the ids the feed sends - fuzzy
 * matching disabled or not configured. It never has a verdict, so every caller keeps the error of
 * the exact lookup it was doing.
 */
public class NoOpFuzzyTripMatcher implements FuzzyTripMatcher {

  public static final NoOpFuzzyTripMatcher INSTANCE = new NoOpFuzzyTripMatcher();

  private NoOpFuzzyTripMatcher() {}

  @Override
  public Optional<TripAndPattern> match(
    TripReference tripReference,
    TripUpdateCommand command,
    LocalDate serviceDate
  ) {
    return Optional.empty();
  }
}
