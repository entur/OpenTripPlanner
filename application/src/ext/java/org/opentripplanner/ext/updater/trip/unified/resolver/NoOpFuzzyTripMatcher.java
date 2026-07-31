package org.opentripplanner.ext.updater.trip.unified.resolver;

import java.time.LocalDate;
import org.opentripplanner.ext.updater.trip.unified.model.command.ExistingTripCommand;
import org.opentripplanner.ext.updater.trip.unified.model.command.TripReference;
import org.opentripplanner.updater.spi.UpdateErrorType;
import org.opentripplanner.updater.spi.UpdateException;

/**
 * A no-op fuzzy trip matcher that always throws UpdateException.
 * Used when fuzzy matching is disabled or not configured.
 */
public class NoOpFuzzyTripMatcher implements FuzzyTripMatcher {

  public static final NoOpFuzzyTripMatcher INSTANCE = new NoOpFuzzyTripMatcher();

  private NoOpFuzzyTripMatcher() {}

  @Override
  public TripAndPattern match(
    TripReference tripReference,
    ExistingTripCommand command,
    LocalDate serviceDate
  ) {
    throw UpdateException.of(tripReference.tripId(), UpdateErrorType.NO_FUZZY_TRIP_MATCH);
  }

  @Override
  public boolean isEnabled() {
    return false;
  }
}
