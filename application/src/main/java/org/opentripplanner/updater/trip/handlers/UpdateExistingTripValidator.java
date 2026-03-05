package org.opentripplanner.updater.trip.handlers;

import org.opentripplanner.transit.model.framework.Result;
import org.opentripplanner.updater.spi.UpdateError;
import org.opentripplanner.updater.trip.model.ResolvedExistingTrip;
import org.opentripplanner.updater.trip.model.StopUpdateStrategy;

/**
 * Validates preconditions for {@link UpdateExistingTripHandler}.
 * <p>
 * Checks FULL_UPDATE constraints:
 * <ul>
 *   <li>Must not use stopSequence</li>
 *   <li>Stop count must exactly match the pattern</li>
 * </ul>
 * PARTIAL_UPDATE is always valid (returns success).
 */
public class UpdateExistingTripValidator implements TripUpdateValidator.ForExistingTrip {

  @Override
  public Result<Void, UpdateError> validate(ResolvedExistingTrip resolvedUpdate) {
    var stopUpdateStrategy = resolvedUpdate.options().stopUpdateStrategy();

    if (stopUpdateStrategy != StopUpdateStrategy.FULL_UPDATE) {
      return Result.success(null);
    }

    var tripId = resolvedUpdate.trip().getId();
    var scheduledPattern = resolvedUpdate.scheduledPattern();
    var stopTimeUpdates = resolvedUpdate.stopTimeUpdates();

    // FULL_UPDATE must not use stopSequence
    if (resolvedUpdate.hasStopSequences()) {
      return Result.failure(
        new UpdateError(tripId, UpdateError.UpdateErrorType.INVALID_STOP_SEQUENCE)
      );
    }

    // FULL_UPDATE must have exact stop count match against the scheduled pattern.
    // We compare against the scheduled pattern (not the current real-time pattern) because
    // a revert update may send fewer stops than a previously modified pattern (e.g. after
    // removing an extra call).
    if (stopTimeUpdates.size() < scheduledPattern.numberOfStops()) {
      return Result.failure(new UpdateError(tripId, UpdateError.UpdateErrorType.TOO_FEW_STOPS));
    }
    if (stopTimeUpdates.size() > scheduledPattern.numberOfStops()) {
      return Result.failure(new UpdateError(tripId, UpdateError.UpdateErrorType.TOO_MANY_STOPS));
    }

    return Result.success(null);
  }
}
