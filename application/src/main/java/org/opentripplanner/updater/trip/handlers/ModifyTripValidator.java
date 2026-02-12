package org.opentripplanner.updater.trip.handlers;

import java.util.List;
import org.opentripplanner.transit.model.framework.Result;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.site.StopLocation;
import org.opentripplanner.transit.model.timetable.Trip;
import org.opentripplanner.updater.spi.UpdateError;
import org.opentripplanner.updater.trip.model.ResolvedExistingTrip;
import org.opentripplanner.updater.trip.model.ResolvedStopTimeUpdate;
import org.opentripplanner.updater.trip.model.StopReplacementConstraint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Validates preconditions for {@link ModifyTripHandler}.
 * <p>
 * Checks:
 * <ul>
 *   <li>Minimum stops (>= 2)</li>
 *   <li>SIRI extra call constraints: non-extra stop count must match original pattern,
 *       and each non-extra stop must match the original via {@link StopReplacementValidator}</li>
 * </ul>
 */
public class ModifyTripValidator implements TripUpdateValidator.ForExistingTrip {

  private static final Logger LOG = LoggerFactory.getLogger(ModifyTripValidator.class);

  @Override
  public Result<Void, UpdateError> validate(ResolvedExistingTrip resolvedUpdate) {
    var trip = resolvedUpdate.trip();
    var stopTimeUpdates = resolvedUpdate.stopTimeUpdates();

    // Validate minimum stops
    if (stopTimeUpdates.size() < 2) {
      LOG.debug("MODIFY_TRIP: trip {} has fewer than 2 stops, skipping.", trip.getId());
      return Result.failure(
        new UpdateError(trip.getId(), UpdateError.UpdateErrorType.TOO_FEW_STOPS)
      );
    }

    // Check if this is a SIRI extra call (has isExtraCall flags)
    boolean hasSiriExtraCalls = stopTimeUpdates
      .stream()
      .anyMatch(ResolvedStopTimeUpdate::isExtraCall);

    // Validate SIRI extra call constraints
    if (hasSiriExtraCalls) {
      return validateSiriExtraCalls(
        stopTimeUpdates,
        resolvedUpdate.scheduledPattern(),
        trip,
        resolvedUpdate.options().stopReplacementConstraint()
      );
    }

    return Result.success(null);
  }

  /**
   * Validate SIRI extra call constraints.
   * Non-extra stops must match the original pattern according to the stop replacement constraint.
   */
  private Result<Void, UpdateError> validateSiriExtraCalls(
    List<ResolvedStopTimeUpdate> stopTimeUpdates,
    TripPattern originalPattern,
    Trip trip,
    StopReplacementConstraint constraint
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
      return Result.failure(
        new UpdateError(trip.getId(), UpdateError.UpdateErrorType.INVALID_STOP_SEQUENCE)
      );
    }

    var validator = new StopReplacementValidator();

    // Validate each non-extra stop matches the original pattern
    int originalIndex = 0;
    for (int i = 0; i < stopTimeUpdates.size(); i++) {
      var stopUpdate = stopTimeUpdates.get(i);
      if (stopUpdate.isExtraCall()) {
        continue;
      }

      StopLocation updateStop = stopUpdate.stop();
      if (updateStop == null) {
        return Result.failure(
          new UpdateError(trip.getId(), UpdateError.UpdateErrorType.UNKNOWN_STOP, i)
        );
      }

      StopLocation originalStop = originalPattern.getStop(originalIndex);

      // Use the configured stop replacement constraint for validation
      var validationResult = validator.validate(originalStop, updateStop, constraint);
      if (validationResult != StopReplacementValidator.Result.VALID) {
        LOG.debug(
          "SIRI extra call validation failed: stop {} at index {} doesn't match original stop {} ({})",
          updateStop.getId(),
          i,
          originalStop.getId(),
          validationResult
        );
        return Result.failure(
          new UpdateError(trip.getId(), UpdateError.UpdateErrorType.STOP_MISMATCH, i)
        );
      }

      originalIndex++;
    }

    return Result.success(null);
  }
}
