package org.opentripplanner.updater.trip;

import java.time.LocalDate;
import org.opentripplanner.updater.spi.UpdateErrorType;
import org.opentripplanner.updater.spi.UpdateException;
import org.opentripplanner.updater.trip.model.ExistingTripUpdate;
import org.opentripplanner.updater.trip.model.TripReference;

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
    ExistingTripUpdate parsedUpdate,
    LocalDate serviceDate
  ) {
    throw UpdateException.of(tripReference.tripId(), UpdateErrorType.NO_FUZZY_TRIP_MATCH);
  }

  @Override
  public boolean isEnabled() {
    return false;
  }
}
