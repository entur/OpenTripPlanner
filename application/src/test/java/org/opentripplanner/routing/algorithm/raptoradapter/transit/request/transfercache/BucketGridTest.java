package org.opentripplanner.routing.algorithm.raptoradapter.transit.request.transfercache;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class BucketGridTest {

  @Test
  void snapToStep_roundsToNearestMultiple() {
    // 1.42 is closer to 1.40 than to 1.45.
    assertEquals(1.40, BucketGrid.snapToStep(1.42, 0.05), 0.0);
    // 1.43 crosses the half-way mark and rounds up to 1.45.
    assertEquals(1.45, BucketGrid.snapToStep(1.43, 0.05), 0.0);
    // Exact multiples stay put.
    assertEquals(1.40, BucketGrid.snapToStep(1.40, 0.05), 0.0);
  }

  @Test
  void snapToStep_roundsTiesUp() {
    assertEquals(1.45, BucketGrid.snapToStep(1.425, 0.05), 0.0);
    assertEquals(2.0, BucketGrid.snapToStep(1.95, 0.1), 0.0);
  }

  @Test
  void snapToStep_avoidsIeee754DriftAtBoundaries() {
    // 2.05 is not exactly representable in IEEE-754; a naive (value / step) round-trip
    // can drift to 2.0. BigDecimal pinning ensures the half-up correctly rounds to 2.1.
    assertEquals(2.1, BucketGrid.snapToStep(2.05, 0.1), 0.0);
  }

  @Test
  void reluctanceStep_isTieredOnBreakpoints() {
    // Below 3.0 -> step 0.1.
    assertEquals(0.1, BucketGrid.reluctanceStep(0.0));
    assertEquals(0.1, BucketGrid.reluctanceStep(2.99));
    // [3.0, 10.0) -> step 0.5.
    assertEquals(0.5, BucketGrid.reluctanceStep(3.0));
    assertEquals(0.5, BucketGrid.reluctanceStep(9.99));
    // >= 10.0 -> step 1.0.
    assertEquals(1.0, BucketGrid.reluctanceStep(10.0));
    assertEquals(1.0, BucketGrid.reluctanceStep(100.0));
  }
}
