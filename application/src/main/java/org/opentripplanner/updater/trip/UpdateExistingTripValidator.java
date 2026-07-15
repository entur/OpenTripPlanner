package org.opentripplanner.updater.trip;

import org.opentripplanner.updater.spi.UpdateErrorType;
import org.opentripplanner.updater.spi.UpdateException;
import org.opentripplanner.updater.trip.model.ResolvedExistingTrip;

/**
 * Validates preconditions for the {@link TripReviser}, between resolution and the mutation of
 * the trip times.
 * <p>
 * Checks FULL_UPDATE constraints:
 * <ul>
 *   <li>Must not use stopSequence</li>
 *   <li>Stop count must exactly match the pattern</li>
 * </ul>
 * PARTIAL_UPDATE is always valid (returns without throwing).
 */
public class UpdateExistingTripValidator {

  public void validate(ResolvedExistingTrip resolvedUpdate) {
    // The exact-stop-count precondition only applies to position-based (FULL_UPDATE) matching.
    if (!resolvedUpdate.formatPolicy().stopMatching().requiresExactStopCount()) {
      return;
    }

    var tripId = resolvedUpdate.trip().getId();
    var scheduledPattern = resolvedUpdate.scheduledPattern();
    var stopTimeUpdates = resolvedUpdate.stopTimeUpdates();

    // FULL_UPDATE must not use stopSequence
    if (resolvedUpdate.hasStopSequences()) {
      throw UpdateException.of(tripId, UpdateErrorType.INVALID_STOP_SEQUENCE);
    }

    // FULL_UPDATE must have exact stop count match against the scheduled pattern.
    // We compare against the scheduled pattern (not the current real-time pattern) because
    // a revert update may send fewer stops than a previously modified pattern (e.g. after
    // removing an extra call).
    if (stopTimeUpdates.size() < scheduledPattern.numberOfStops()) {
      throw UpdateException.of(tripId, UpdateErrorType.TOO_FEW_STOPS);
    }
    if (stopTimeUpdates.size() > scheduledPattern.numberOfStops()) {
      throw UpdateException.of(tripId, UpdateErrorType.TOO_MANY_STOPS);
    }
  }
}
