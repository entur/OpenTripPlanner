package org.opentripplanner.routing.algorithm.raptoradapter.transit.request.transfercache;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Discrete grid that {@link TransferRequestBucketer} snaps client-supplied speeds and
 * reluctances onto, plus the rounding primitive used to do the snapping. Pure utility,
 * no state.
 */
final class BucketGrid {

  static final double WALK_SPEED_BUCKET = 0.05;
  static final double BIKE_SPEED_BUCKET = 0.1;

  private static final double LOW_RELUCTANCE_BREAKPOINT = 3.0;
  private static final double HIGH_RELUCTANCE_BREAKPOINT = 10.0;
  private static final double LOW_RELUCTANCE_STEP = 0.1;
  private static final double MID_RELUCTANCE_STEP = 0.5;
  private static final double HIGH_RELUCTANCE_STEP = 1.0;

  private BucketGrid() {}

  /**
   * Round {@code value} to the nearest multiple of {@code step}, with ties rounded up.
   * BigDecimal is used to avoid floating-point bias at bucket boundaries (e.g. so that
   * {@code 2.05 / 0.1} is treated as exactly 20.5 and rounds to 2.1 rather than drifting
   * to 2.0 through IEEE-754 representation of 0.1).
   */
  static double snapToStep(double value, double step) {
    return BigDecimal.valueOf(value)
      .divide(BigDecimal.valueOf(step), 0, RoundingMode.HALF_UP)
      .multiply(BigDecimal.valueOf(step))
      .doubleValue();
  }

  /** Tiered reluctance step: 0.1 below 3.0, 0.5 in [3.0, 10.0), 1.0 at/above 10.0. */
  static double reluctanceStep(double reluctance) {
    if (reluctance < LOW_RELUCTANCE_BREAKPOINT) {
      return LOW_RELUCTANCE_STEP;
    }
    if (reluctance < HIGH_RELUCTANCE_BREAKPOINT) {
      return MID_RELUCTANCE_STEP;
    }
    return HIGH_RELUCTANCE_STEP;
  }
}
