package org.opentripplanner.updater.trip.policy;

import javax.annotation.Nullable;
import org.opentripplanner.transit.model.site.StopLocation;

/**
 * The single specification deciding whether a real-time message may replace a scheduled stop with
 * a different stop. Consulted by both the apply path ({@code UpdateExistingTripHandler}) and the
 * {@code ModifyTripValidator}, replacing the format-divergent {@code StopReplacementConstraint}
 * enum and the former {@code StopReplacementValidator}.
 */
public sealed interface StopReplacementPolicy
  permits
    StopReplacementPolicy.AnyStop,
    StopReplacementPolicy.SameParentStation,
    StopReplacementPolicy.NotAllowed {
  enum Result {
    /** The stop (replacement) is allowed. */
    VALID,
    /** The replacement violates the policy. */
    STOP_MISMATCH,
  }

  /**
   * Check whether using {@code actual} in place of the {@code scheduled} stop is allowed. A
   * {@code null} actual, or one with the same id as the scheduled stop, is not a replacement and is
   * always {@link Result#VALID}.
   */
  default Result check(StopLocation scheduled, @Nullable StopLocation actual) {
    if (actual == null) {
      return Result.VALID;
    }
    if (actual.getId().equals(scheduled.getId())) {
      return Result.VALID;
    }
    return checkReplacement(scheduled, actual);
  }

  /** Apply the policy to a genuine replacement (a different, non-null stop). */
  Result checkReplacement(StopLocation scheduled, StopLocation actual);

  /** GTFS-RT: replacement trips may use any stops. */
  StopReplacementPolicy ANY_STOP = new AnyStop();
  /** SIRI-ET: a replacement must be within the same parent station (quay/platform change). */
  StopReplacementPolicy SAME_PARENT_STATION = new SameParentStation();
  /** Stop pattern modification not allowed. */
  StopReplacementPolicy NOT_ALLOWED = new NotAllowed();

  final class AnyStop implements StopReplacementPolicy {

    @Override
    public Result checkReplacement(StopLocation scheduled, StopLocation actual) {
      return Result.VALID;
    }
  }

  final class NotAllowed implements StopReplacementPolicy {

    @Override
    public Result checkReplacement(StopLocation scheduled, StopLocation actual) {
      return Result.STOP_MISMATCH;
    }
  }

  final class SameParentStation implements StopReplacementPolicy {

    @Override
    public Result checkReplacement(StopLocation scheduled, StopLocation actual) {
      var scheduledParent = scheduled.getParentStation();
      var actualParent = actual.getParentStation();

      if (scheduledParent == null || actualParent == null) {
        return Result.STOP_MISMATCH;
      }
      if (!scheduledParent.getId().equals(actualParent.getId())) {
        return Result.STOP_MISMATCH;
      }
      return Result.VALID;
    }
  }
}
