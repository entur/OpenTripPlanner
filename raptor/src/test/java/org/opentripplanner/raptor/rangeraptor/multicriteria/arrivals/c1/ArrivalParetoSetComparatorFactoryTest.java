package org.opentripplanner.raptor.rangeraptor.multicriteria.arrivals.c1;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.opentripplanner.raptor._data.transit.TestTripSchedule;
import org.opentripplanner.raptor.api.model.GeneralizedCostRelaxFunction;
import org.opentripplanner.raptor.api.model.RelaxFunction;
import org.opentripplanner.raptor.api.view.PathLegType;

class ArrivalParetoSetComparatorFactoryTest {

  private static final int STOP = 9;
  private static final boolean ARRIVED_ON_BOARD = true;
  private static final boolean ARRIVED_ON_FOOT = false;
  private static final int C1_100 = 100;
  private static final int C1_777 = 777;
  private static final int PARETO_ROUND_ONE = 1;
  private static final int PARETO_ROUND_TWO = 2;
  private static final int ARRIVAL_TIME_EARLY = 12;
  private static final int ARRIVAL_TIME_LATE = 13;

  private static final ArrivalParetoSetComparatorFactory<A> COMPARATOR_C1 =
    ArrivalParetoSetComparatorFactory.factory(RelaxFunction.NORMAL, null);

  private static final ArrivalParetoSetComparatorFactory<A> COMPARATOR_C1_AND_C2 =
    ArrivalParetoSetComparatorFactory.factory(RelaxFunction.NORMAL, (left, right) -> left > right);

  @Test
  void compareArrivalTimeRoundAndCost() {
    // Same values for arrival-time, pareto-round and c1. Ignore c2 and arrivedOnBoard
    assertFalse(
      COMPARATOR_C1.compareArrivalTimeRoundAndCost().leftDominanceExist(
        new A(ARRIVAL_TIME_EARLY, PARETO_ROUND_ONE, C1_100, C1_100, ARRIVED_ON_BOARD),
        new A(ARRIVAL_TIME_EARLY, PARETO_ROUND_ONE, C1_100, C1_777, ARRIVED_ON_FOOT)
      )
    );
    // Arrival-time is better
    assertTrue(
      COMPARATOR_C1.compareArrivalTimeRoundAndCost().leftDominanceExist(
        new A(ARRIVAL_TIME_EARLY, PARETO_ROUND_TWO, C1_777, C1_777, ARRIVED_ON_FOOT),
        new A(ARRIVAL_TIME_LATE, PARETO_ROUND_ONE, C1_100, C1_100, ARRIVED_ON_BOARD)
      )
    );
    // Pareto-round is better
    assertTrue(
      COMPARATOR_C1.compareArrivalTimeRoundAndCost().leftDominanceExist(
        new A(ARRIVAL_TIME_EARLY, PARETO_ROUND_ONE, C1_777, C1_777, ARRIVED_ON_FOOT),
        new A(ARRIVAL_TIME_EARLY, PARETO_ROUND_TWO, C1_100, C1_100, ARRIVED_ON_BOARD)
      )
    );
    // C1 is better
    assertTrue(
      COMPARATOR_C1.compareArrivalTimeRoundAndCost().leftDominanceExist(
        new A(ARRIVAL_TIME_EARLY, PARETO_ROUND_ONE, C1_100, C1_777, ARRIVED_ON_FOOT),
        new A(ARRIVAL_TIME_EARLY, PARETO_ROUND_ONE, C1_777, C1_100, ARRIVED_ON_BOARD)
      )
    );
  }

  @Test
  void compareArrivalTimeRoundAndCostWithC2() {
    // Same values for arrival-time, pareto-round and c1. Ignore c2 and arrivedOnBoard
    assertFalse(
      COMPARATOR_C1_AND_C2.compareArrivalTimeRoundAndCost().leftDominanceExist(
        new A(ARRIVAL_TIME_EARLY, PARETO_ROUND_ONE, C1_100, C1_100, ARRIVED_ON_BOARD),
        new A(ARRIVAL_TIME_EARLY, PARETO_ROUND_ONE, C1_100, C1_100, ARRIVED_ON_FOOT)
      )
    );
    // Arrival-time is better
    assertTrue(
      COMPARATOR_C1_AND_C2.compareArrivalTimeRoundAndCost().leftDominanceExist(
        new A(ARRIVAL_TIME_EARLY, PARETO_ROUND_TWO, C1_777, C1_100, ARRIVED_ON_FOOT),
        new A(ARRIVAL_TIME_LATE, PARETO_ROUND_ONE, C1_100, C1_100, ARRIVED_ON_BOARD)
      )
    );
    // Pareto-round is better
    assertTrue(
      COMPARATOR_C1_AND_C2.compareArrivalTimeRoundAndCost().leftDominanceExist(
        new A(ARRIVAL_TIME_EARLY, PARETO_ROUND_ONE, C1_777, C1_100, ARRIVED_ON_FOOT),
        new A(ARRIVAL_TIME_EARLY, PARETO_ROUND_TWO, C1_100, C1_100, ARRIVED_ON_BOARD)
      )
    );
    // C1 is better
    assertTrue(
      COMPARATOR_C1_AND_C2.compareArrivalTimeRoundAndCost().leftDominanceExist(
        new A(ARRIVAL_TIME_EARLY, PARETO_ROUND_ONE, C1_100, C1_100, ARRIVED_ON_FOOT),
        new A(ARRIVAL_TIME_EARLY, PARETO_ROUND_ONE, C1_777, C1_100, ARRIVED_ON_BOARD)
      )
    );

    // C2 is better
    assertTrue(
      COMPARATOR_C1_AND_C2.compareArrivalTimeRoundAndCost().leftDominanceExist(
        new A(ARRIVAL_TIME_EARLY, PARETO_ROUND_ONE, C1_100, C1_777, ARRIVED_ON_FOOT),
        new A(ARRIVAL_TIME_EARLY, PARETO_ROUND_ONE, C1_100, C1_100, ARRIVED_ON_BOARD)
      )
    );
  }

  @Test
  void compareArrivalTimeRoundCostAndOnBoardArrivalWithC2() {
    // Same values for arrival-time, pareto-round and c1. Ignore c2 and arrivedOnBoard
    assertFalse(
      COMPARATOR_C1_AND_C2.compareArrivalTimeRoundCostAndOnBoardArrival().leftDominanceExist(
        new A(ARRIVAL_TIME_EARLY, PARETO_ROUND_ONE, C1_100, C1_100, ARRIVED_ON_BOARD),
        new A(ARRIVAL_TIME_EARLY, PARETO_ROUND_ONE, C1_100, C1_100, ARRIVED_ON_BOARD)
      )
    );
    // Arrival-time is better
    assertTrue(
      COMPARATOR_C1_AND_C2.compareArrivalTimeRoundCostAndOnBoardArrival().leftDominanceExist(
        new A(ARRIVAL_TIME_EARLY, PARETO_ROUND_TWO, C1_777, C1_100, ARRIVED_ON_FOOT),
        new A(ARRIVAL_TIME_LATE, PARETO_ROUND_ONE, C1_100, C1_100, ARRIVED_ON_BOARD)
      )
    );
    // Pareto-round is better
    assertTrue(
      COMPARATOR_C1_AND_C2.compareArrivalTimeRoundCostAndOnBoardArrival().leftDominanceExist(
        new A(ARRIVAL_TIME_LATE, PARETO_ROUND_ONE, C1_777, C1_100, ARRIVED_ON_FOOT),
        new A(ARRIVAL_TIME_EARLY, PARETO_ROUND_TWO, C1_100, C1_100, ARRIVED_ON_BOARD)
      )
    );
    // C1 is better
    assertTrue(
      COMPARATOR_C1_AND_C2.compareArrivalTimeRoundCostAndOnBoardArrival().leftDominanceExist(
        new A(ARRIVAL_TIME_LATE, PARETO_ROUND_TWO, C1_100, C1_100, ARRIVED_ON_FOOT),
        new A(ARRIVAL_TIME_EARLY, PARETO_ROUND_ONE, C1_777, C1_100, ARRIVED_ON_BOARD)
      )
    );
    // Arrived on-board is better
    assertTrue(
      COMPARATOR_C1_AND_C2.compareArrivalTimeRoundCostAndOnBoardArrival().leftDominanceExist(
        new A(ARRIVAL_TIME_LATE, PARETO_ROUND_TWO, C1_777, C1_100, ARRIVED_ON_BOARD),
        new A(ARRIVAL_TIME_EARLY, PARETO_ROUND_ONE, C1_100, C1_100, ARRIVED_ON_FOOT)
      )
    );
    // C2 is better
    assertTrue(
      COMPARATOR_C1_AND_C2.compareArrivalTimeRoundCostAndOnBoardArrival().leftDominanceExist(
        new A(ARRIVAL_TIME_LATE, PARETO_ROUND_TWO, C1_100, C1_777, ARRIVED_ON_FOOT),
        new A(ARRIVAL_TIME_EARLY, PARETO_ROUND_ONE, C1_100, C1_100, ARRIVED_ON_BOARD)
      )
    );
  }

  @Test
  void compareArrivalTimeRoundCostAndOnBoardArrival() {
    // Same values for arrival-time, pareto-round and c1. Ignore c2 and arrivedOnBoard
    assertFalse(
      COMPARATOR_C1.compareArrivalTimeRoundCostAndOnBoardArrival().leftDominanceExist(
        new A(ARRIVAL_TIME_EARLY, PARETO_ROUND_ONE, C1_100, C1_100, ARRIVED_ON_BOARD),
        new A(ARRIVAL_TIME_EARLY, PARETO_ROUND_ONE, C1_100, C1_777, ARRIVED_ON_BOARD)
      )
    );
    // Arrival-time is better
    assertTrue(
      COMPARATOR_C1.compareArrivalTimeRoundCostAndOnBoardArrival().leftDominanceExist(
        new A(ARRIVAL_TIME_EARLY, PARETO_ROUND_TWO, C1_777, C1_777, ARRIVED_ON_FOOT),
        new A(ARRIVAL_TIME_LATE, PARETO_ROUND_ONE, C1_100, C1_100, ARRIVED_ON_BOARD)
      )
    );
    // Pareto-round is better
    assertTrue(
      COMPARATOR_C1.compareArrivalTimeRoundCostAndOnBoardArrival().leftDominanceExist(
        new A(ARRIVAL_TIME_LATE, PARETO_ROUND_ONE, C1_777, C1_777, ARRIVED_ON_FOOT),
        new A(ARRIVAL_TIME_EARLY, PARETO_ROUND_TWO, C1_100, C1_100, ARRIVED_ON_BOARD)
      )
    );
    // C1 is better
    assertTrue(
      COMPARATOR_C1.compareArrivalTimeRoundCostAndOnBoardArrival().leftDominanceExist(
        new A(ARRIVAL_TIME_LATE, PARETO_ROUND_TWO, C1_100, C1_777, ARRIVED_ON_FOOT),
        new A(ARRIVAL_TIME_EARLY, PARETO_ROUND_ONE, C1_777, C1_100, ARRIVED_ON_BOARD)
      )
    );
    // Arrived on-board is better
    assertTrue(
      COMPARATOR_C1.compareArrivalTimeRoundCostAndOnBoardArrival().leftDominanceExist(
        new A(ARRIVAL_TIME_LATE, PARETO_ROUND_TWO, C1_777, C1_777, ARRIVED_ON_BOARD),
        new A(ARRIVAL_TIME_EARLY, PARETO_ROUND_ONE, C1_100, C1_100, ARRIVED_ON_FOOT)
      )
    );
  }

  @Test
  void compareRelaxedC1Test() {
    int bestC1 = 600;
    int okC1 = 799;
    int rejectC1 = okC1 + 1;
    var relaxC1 = GeneralizedCostRelaxFunction.of(1.0, 200);
    var referenceArrival = new A(
      ARRIVAL_TIME_EARLY,
      PARETO_ROUND_ONE,
      bestC1,
      C1_100,
      ARRIVED_ON_BOARD
    );

    var subject = ArrivalParetoSetComparatorFactory.factory(relaxC1, null);

    assertFalse(
      subject
        .compareArrivalTimeRoundAndCost()
        .leftDominanceExist(
          new A(ARRIVAL_TIME_LATE, PARETO_ROUND_TWO, rejectC1, C1_777, ARRIVED_ON_FOOT),
          referenceArrival
        )
    );
    assertTrue(
      subject
        .compareArrivalTimeRoundAndCost()
        .leftDominanceExist(
          new A(ARRIVAL_TIME_LATE, PARETO_ROUND_TWO, okC1, C1_777, ARRIVED_ON_FOOT),
          referenceArrival
        )
    );

    // Test OnBoardArrival
    assertFalse(
      subject
        .compareArrivalTimeRoundCostAndOnBoardArrival()
        .leftDominanceExist(
          new A(ARRIVAL_TIME_LATE, PARETO_ROUND_TWO, rejectC1, C1_777, ARRIVED_ON_FOOT),
          referenceArrival
        )
    );
    assertTrue(
      subject
        .compareArrivalTimeRoundCostAndOnBoardArrival()
        .leftDominanceExist(
          new A(ARRIVAL_TIME_LATE, PARETO_ROUND_TWO, okC1, C1_777, ARRIVED_ON_FOOT),
          referenceArrival
        )
    );
  }

  @Test
  void testCompareBase() {
    // Same values for arrival-time, pareto-round and c1. Ignore c2 and arrivedOnBoard
    assertFalse(
      ArrivalParetoSetComparatorFactory.compareBase(
        new A(ARRIVAL_TIME_EARLY, PARETO_ROUND_ONE, C1_100, C1_100, ARRIVED_ON_BOARD),
        new A(ARRIVAL_TIME_EARLY, PARETO_ROUND_ONE, C1_100, C1_777, ARRIVED_ON_FOOT)
      )
    );
    // Arrival-time is better
    assertTrue(
      ArrivalParetoSetComparatorFactory.compareBase(
        new A(ARRIVAL_TIME_EARLY, PARETO_ROUND_TWO, C1_777, C1_777, ARRIVED_ON_FOOT),
        new A(ARRIVAL_TIME_LATE, PARETO_ROUND_ONE, C1_100, C1_100, ARRIVED_ON_BOARD)
      )
    );
    // Pareto-round is better
    assertTrue(
      ArrivalParetoSetComparatorFactory.compareBase(
        new A(ARRIVAL_TIME_EARLY, PARETO_ROUND_ONE, C1_777, C1_777, ARRIVED_ON_FOOT),
        new A(ARRIVAL_TIME_EARLY, PARETO_ROUND_TWO, C1_100, C1_100, ARRIVED_ON_BOARD)
      )
    );
    // C1 is better
    assertTrue(
      ArrivalParetoSetComparatorFactory.compareBase(
        new A(ARRIVAL_TIME_EARLY, PARETO_ROUND_ONE, C1_100, C1_777, ARRIVED_ON_FOOT),
        new A(ARRIVAL_TIME_EARLY, PARETO_ROUND_ONE, C1_777, C1_100, ARRIVED_ON_BOARD)
      )
    );
  }

  @Test
  void testCompareArrivedOnBoard() {
    // Same values for arrivedOnBoard. Ignore arrival-time, pareto-round, c1 and c2
    assertFalse(
      ArrivalParetoSetComparatorFactory.compareArrivedOnBoard(
        new A(ARRIVAL_TIME_EARLY, PARETO_ROUND_ONE, C1_100, C1_100, ARRIVED_ON_BOARD),
        new A(ARRIVAL_TIME_LATE, PARETO_ROUND_TWO, C1_777, C1_777, ARRIVED_ON_BOARD)
      )
    );
    // Arrived on-board is better
    assertTrue(
      ArrivalParetoSetComparatorFactory.compareArrivedOnBoard(
        new A(ARRIVAL_TIME_LATE, PARETO_ROUND_TWO, C1_777, C1_777, ARRIVED_ON_BOARD),
        new A(ARRIVAL_TIME_EARLY, PARETO_ROUND_ONE, C1_100, C1_100, ARRIVED_ON_FOOT)
      )
    );
  }

  @Test
  void testRelaxedCompareBase() {
    int bestC1 = 600;
    int okC1 = 899;
    int rejectC1 = okC1 + 1;

    RelaxFunction relaxC1 = GeneralizedCostRelaxFunction.of(1.25, 150);

    // Test same values arrival-time, round and c1 should not dominate.
    // Ignore better c2 and onBoardArrival
    assertFalse(
      ArrivalParetoSetComparatorFactory.relaxedCompareBase(
        relaxC1,
        new A(ARRIVAL_TIME_EARLY, PARETO_ROUND_ONE, rejectC1, C1_100, ARRIVED_ON_BOARD),
        new A(ARRIVAL_TIME_EARLY, PARETO_ROUND_ONE, bestC1, C1_777, ARRIVED_ON_FOOT)
      )
    );

    // Arrival-time better, other the same
    assertTrue(
      ArrivalParetoSetComparatorFactory.relaxedCompareBase(
        relaxC1,
        new A(ARRIVAL_TIME_EARLY, PARETO_ROUND_ONE, rejectC1, C1_100, ARRIVED_ON_BOARD),
        new A(ARRIVAL_TIME_LATE, PARETO_ROUND_ONE, bestC1, C1_100, ARRIVED_ON_BOARD)
      )
    );

    // Round better, other the same
    assertTrue(
      ArrivalParetoSetComparatorFactory.relaxedCompareBase(
        relaxC1,
        new A(ARRIVAL_TIME_EARLY, PARETO_ROUND_ONE, rejectC1, C1_100, ARRIVED_ON_BOARD),
        new A(ARRIVAL_TIME_EARLY, PARETO_ROUND_TWO, bestC1, C1_100, ARRIVED_ON_BOARD)
      )
    );
    // C1 is better, other the same
    assertTrue(
      ArrivalParetoSetComparatorFactory.relaxedCompareBase(
        relaxC1,
        new A(ARRIVAL_TIME_EARLY, PARETO_ROUND_ONE, okC1, C1_100, ARRIVED_ON_BOARD),
        new A(ARRIVAL_TIME_EARLY, PARETO_ROUND_ONE, bestC1, C1_100, ARRIVED_ON_BOARD)
      )
    );
  }

  private static class A extends McStopArrival<TestTripSchedule> {

    int c2;
    boolean arrivedOnBoard;

    public A(int arrivalTime, int paretoRound, int c1, int c2, boolean arrivedOnBoard) {
      super(STOP, 0, arrivalTime, c1, paretoRound);
      this.c2 = c2;
      this.arrivedOnBoard = arrivedOnBoard;
    }

    @Override
    public int c2() {
      return c2;
    }

    @Override
    public PathLegType arrivedBy() {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean arrivedOnBoard() {
      return arrivedOnBoard;
    }

    @Override
    public McStopArrival<TestTripSchedule> addSlackToArrivalTime(int slack) {
      throw new UnsupportedOperationException();
    }
  }
}
