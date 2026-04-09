package org.opentripplanner.raptor._data.transit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Verify that {@link TestTripSchedule} satisfies the {@code relativeTravelTime} contract defined
 * in {@link org.opentripplanner.raptor.spi.RaptorTripSchedule#relativeTravelTime(int)}.
 */
class TestTripScheduleTest {

  private static final int HEADWAY = 600;

  /**
   * Two trips in the same pattern, 10 minutes apart.
   *
   * <pre>
   *   Stop:    A       B       C
   *   Trip 1: 10:00  10:05  10:10
   *   Trip 2: 10:10  10:15  10:20  (Trip 1 + 10 min headway)
   * </pre>
   */
  private final TestTripSchedule trip1 = TestTripSchedule.schedule("10:00 10:05 10:10").build();
  private final TestTripSchedule trip2 = TestTripSchedule.schedule()
    .times(trip1.arrival(0) + HEADWAY, trip1.arrival(1) + HEADWAY, trip1.arrival(2) + HEADWAY)
    .build();

  @Test
  void relativeTravelTimeDecreasesByActualTransitTimeBetweenStops() {
    int boardAtA = trip1.departure(0);
    int boardAtB = trip1.departure(1);
    int actualTimeBetweenAAndB = boardAtB - boardAtA;

    assertEquals(
      actualTimeBetweenAAndB,
      trip1.relativeTravelTime(boardAtA) - trip1.relativeTravelTime(boardAtB)
    );
  }

  @Test
  void relativeTravelTimeIsIdenticalAcrossTripsInTheSamePatternForEquivalentBoardingPositions() {
    assertEquals(
      trip1.relativeTravelTime(trip1.departure(0)),
      trip2.relativeTravelTime(trip2.departure(0))
    );
    assertEquals(
      trip1.relativeTravelTime(trip1.departure(1)),
      trip2.relativeTravelTime(trip2.departure(1))
    );
    assertEquals(
      trip1.relativeTravelTime(trip1.departure(2)),
      trip2.relativeTravelTime(trip2.departure(2))
    );
  }
}
