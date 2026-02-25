package org.opentripplanner.updater.trip.handlers;

import javax.annotation.Nullable;
import org.opentripplanner.transit.model.site.StopLocation;
import org.opentripplanner.updater.trip.model.StopReplacementConstraint;

/**
 * Validates stop replacements against configured constraints.
 * <p>
 * In real-time updates, a stop may be replaced by a different stop (e.g., a different platform
 * within the same station). This validator checks whether such replacements conform to the
 * configured constraint policy.
 */
public class StopReplacementValidator {

  /**
   * Result of stop replacement validation.
   */
  public enum Result {
    /** Validation passed - the stop replacement is allowed. */
    VALID,
    /** The replacement violates the configured constraint. */
    STOP_MISMATCH,
  }

  /**
   * Validates that a stop replacement conforms to the configured constraint.
   *
   * @param scheduledStop The scheduled stop from the pattern
   * @param actualStop The actual stop to be used (null if no replacement)
   * @param constraint The constraint to enforce
   * @return The validation result
   */
  public Result validate(
    StopLocation scheduledStop,
    @Nullable StopLocation actualStop,
    StopReplacementConstraint constraint
  ) {
    // No replacement - using scheduled stop
    if (actualStop == null) {
      return Result.VALID;
    }

    // If the actual stop is the same as the scheduled stop, no replacement
    if (actualStop.getId().equals(scheduledStop.getId())) {
      return Result.VALID;
    }

    // Now we have a replacement - check constraint
    return validateConstraint(scheduledStop, actualStop, constraint);
  }

  private Result validateConstraint(
    StopLocation scheduledStop,
    StopLocation actualStop,
    StopReplacementConstraint constraint
  ) {
    return switch (constraint) {
      case ANY_STOP -> Result.VALID;
      case NOT_ALLOWED -> Result.STOP_MISMATCH;
      case SAME_PARENT_STATION -> {
        var scheduledParent = scheduledStop.getParentStation();
        var actualParent = actualStop.getParentStation();

        if (scheduledParent == null || actualParent == null) {
          yield Result.STOP_MISMATCH;
        }

        if (!scheduledParent.getId().equals(actualParent.getId())) {
          yield Result.STOP_MISMATCH;
        }

        yield Result.VALID;
      }
    };
  }
}
