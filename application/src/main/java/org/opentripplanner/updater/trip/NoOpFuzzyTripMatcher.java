package org.opentripplanner.updater.trip;

import java.time.LocalDate;
import org.opentripplanner.transit.model.framework.Result;
import org.opentripplanner.updater.spi.UpdateError;
import org.opentripplanner.updater.trip.model.ParsedExistingTripUpdate;
import org.opentripplanner.updater.trip.model.TripReference;

/**
 * A no-op fuzzy trip matcher that always returns failure.
 * Used when fuzzy matching is disabled or not configured.
 */
public class NoOpFuzzyTripMatcher implements FuzzyTripMatcher {

  public static final NoOpFuzzyTripMatcher INSTANCE = new NoOpFuzzyTripMatcher();

  private NoOpFuzzyTripMatcher() {}

  @Override
  public Result<TripAndPattern, UpdateError> match(
    TripReference tripReference,
    ParsedExistingTripUpdate parsedUpdate,
    LocalDate serviceDate
  ) {
    return Result.failure(
      new UpdateError(tripReference.tripId(), UpdateError.UpdateErrorType.NO_FUZZY_TRIP_MATCH)
    );
  }
}
