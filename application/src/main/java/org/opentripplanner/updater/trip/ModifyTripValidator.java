package org.opentripplanner.updater.trip;

import java.util.List;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.site.StopLocation;
import org.opentripplanner.transit.model.timetable.Trip;
import org.opentripplanner.updater.spi.UpdateErrorType;
import org.opentripplanner.updater.spi.UpdateException;
import org.opentripplanner.updater.trip.model.ResolvedExistingTrip;
import org.opentripplanner.updater.trip.model.ResolvedStopTimeUpdate;
import org.opentripplanner.updater.trip.policy.StopReplacementPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Validates preconditions for the {@link TripModifier}, between resolution and the construction
 * of the modified pattern.
 * <p>
 * Checks:
 * <ul>
 *   <li>Minimum stops (>= 2)</li>
 *   <li>SIRI extra call constraints: non-extra stop count must match original pattern,
 *       and each non-extra stop must match the original via {@link StopReplacementPolicy}</li>
 * </ul>
 */
public class ModifyTripValidator {

  private static final Logger LOG = LoggerFactory.getLogger(ModifyTripValidator.class);

  public void validate(ResolvedExistingTrip resolvedUpdate) {
    var trip = resolvedUpdate.trip();
    var stopTimeUpdates = resolvedUpdate.stopTimeUpdates();

    // Validate minimum stops
    if (stopTimeUpdates.size() < 2) {
      LOG.debug("MODIFY_TRIP: trip {} has fewer than 2 stops, skipping.", trip.getId());
      throw UpdateException.of(trip.getId(), UpdateErrorType.TOO_FEW_STOPS);
    }

    // Check if this is a SIRI extra call (has isExtraCall flags)
    boolean hasSiriExtraCalls = resolvedUpdate.hasSiriExtraCalls();

    // Validate SIRI extra call constraints
    if (hasSiriExtraCalls) {
      validateSiriExtraCalls(
        stopTimeUpdates,
        resolvedUpdate.scheduledPattern(),
        trip,
        resolvedUpdate.formatPolicy().stopReplacement()
      );
    }
  }

  /**
   * Validate SIRI extra call constraints.
   * Non-extra stops must match the original pattern according to the stop replacement constraint.
   */
  private void validateSiriExtraCalls(
    List<ResolvedStopTimeUpdate> stopTimeUpdates,
    TripPattern originalPattern,
    Trip trip,
    StopReplacementPolicy stopReplacement
  ) {
    // Count non-extra stops
    long nonExtraCount = stopTimeUpdates
      .stream()
      .filter(u -> !u.isExtraCall())
      .count();
    if (nonExtraCount != originalPattern.numberOfStops()) {
      LOG.debug(
        "SIRI extra call validation failed: {} non-extra stops but original pattern has {} stops",
        nonExtraCount,
        originalPattern.numberOfStops()
      );
      throw UpdateException.of(trip.getId(), UpdateErrorType.INVALID_STOP_SEQUENCE);
    }

    // Validate each non-extra stop matches the original pattern
    int originalIndex = 0;
    for (int i = 0; i < stopTimeUpdates.size(); i++) {
      var stopUpdate = stopTimeUpdates.get(i);
      if (stopUpdate.isExtraCall()) {
        continue;
      }

      StopLocation updateStop = stopUpdate.stop();
      if (updateStop == null) {
        throw UpdateException.of(trip.getId(), UpdateErrorType.UNKNOWN_STOP, i);
      }

      StopLocation originalStop = originalPattern.getStop(originalIndex);

      // Use the format's stop replacement policy for validation
      var validationResult = stopReplacement.check(originalStop, updateStop);
      if (validationResult != StopReplacementPolicy.Result.VALID) {
        LOG.debug(
          "SIRI extra call validation failed: stop {} at index {} doesn't match original stop {} ({})",
          updateStop.getId(),
          i,
          originalStop.getId(),
          validationResult
        );
        throw UpdateException.of(trip.getId(), UpdateErrorType.STOP_MISMATCH, i);
      }

      originalIndex++;
    }
  }
}
