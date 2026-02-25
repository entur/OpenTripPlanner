package org.opentripplanner.updater.trip.handlers;

import org.opentripplanner.transit.model.framework.Result;
import org.opentripplanner.updater.spi.UpdateError;
import org.opentripplanner.updater.trip.model.ResolvedNewTrip;
import org.opentripplanner.updater.trip.model.UnknownStopBehavior;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Validates preconditions for {@link AddNewTripHandler}.
 * <p>
 * Checks (for new trip creation only, not updates to existing added trips):
 * <ul>
 *   <li>FAIL mode: all stops must be known</li>
 *   <li>Minimum stops (>= 2) on the original (pre-filter) list</li>
 * </ul>
 * IGNORE-mode filtering and post-filter minimum check stay in the handler
 * since they involve transformation, not pure validation.
 */
public class AddNewTripValidator implements TripUpdateValidator.ForNewTrip {

  private static final Logger LOG = LoggerFactory.getLogger(AddNewTripValidator.class);

  @Override
  public Result<Void, UpdateError> validate(ResolvedNewTrip resolvedUpdate) {
    // Skip validation for updates to existing added trips
    if (resolvedUpdate.isUpdateToExistingTrip()) {
      return Result.success(null);
    }

    var tripId = resolvedUpdate.tripCreationInfo().tripId();
    var stopTimeUpdates = resolvedUpdate.stopTimeUpdates();
    var unknownStopBehavior = resolvedUpdate.options().unknownStopBehavior();

    // FAIL mode: strict validation - fail on unknown stops
    if (unknownStopBehavior == UnknownStopBehavior.FAIL) {
      for (int i = 0; i < stopTimeUpdates.size(); i++) {
        var stopUpdate = stopTimeUpdates.get(i);
        if (stopUpdate.stop() == null) {
          LOG.debug("ADD_TRIP: Unknown stop {} in added trip", stopUpdate.stopReference());
          return Result.failure(
            new UpdateError(tripId, UpdateError.UpdateErrorType.UNKNOWN_STOP, i)
          );
        }
      }
    }

    // Minimum stops check on original list
    if (stopTimeUpdates.size() < 2) {
      LOG.debug("ADD_TRIP: Trip {} has fewer than 2 stops", tripId);
      return Result.failure(new UpdateError(tripId, UpdateError.UpdateErrorType.TOO_FEW_STOPS));
    }

    return Result.success(null);
  }
}
