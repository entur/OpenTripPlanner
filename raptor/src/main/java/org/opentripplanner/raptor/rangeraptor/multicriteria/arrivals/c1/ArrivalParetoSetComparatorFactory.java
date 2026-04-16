package org.opentripplanner.raptor.rangeraptor.multicriteria.arrivals.c1;

import javax.annotation.Nullable;
import org.opentripplanner.raptor.api.model.DominanceFunction;
import org.opentripplanner.raptor.api.model.RelaxFunction;
import org.opentripplanner.raptor.util.paretoset.ParetoComparator;

public interface ArrivalParetoSetComparatorFactory<T extends McStopArrival<?>> {
  /**
   * This comparator is used to compare regular stop arrivals. It uses {@code arrivalTime},
   * {@code paretoRound} and {@code c1} to compare arrivals. It does NOT include
   * {@code arrivedOnBoard}. Normally arriving on-board should give the arrival an advantage
   * - you can continue on foot, walking to the next stop. But, we only do this if it happens
   * in the same Raptor iteration and round - if it does, it is taken care of by the order
   * which the algorithm works - not by this comparator.
   */
  ParetoComparator<T> compareArrivalTimeRoundAndCost();

  /**
   * This includes {@code arrivedOnBoard} in the comparison compared with
   * {@link #compareArrivalTimeRoundAndCost()}.
   */
  ParetoComparator<T> compareArrivalTimeRoundCostAndOnBoardArrival();

  static <T extends McStopArrival<?>> ArrivalParetoSetComparatorFactory<T> factory(
    final RelaxFunction relaxC1,
    @Nullable final DominanceFunction c2DominanceFunction
  ) {
    if (relaxC1.isNormal()) {
      return c2DominanceFunction == null
        ? createFactoryC1()
        : createFactoryC1AndC2(c2DominanceFunction);
    }

    return c2DominanceFunction == null
      ? createFactoryRelaxC1(relaxC1)
      : createFactoryRelaxC2(relaxC1, c2DominanceFunction);
  }

  private static <T extends McStopArrival<?>> ArrivalParetoSetComparatorFactory<
    T
  > createFactoryC1() {
    return new ArrivalParetoSetComparatorFactory<>() {
      @Override
      public ParetoComparator<T> compareArrivalTimeRoundAndCost() {
        return ArrivalParetoSetComparatorFactory::compareBase;
      }

      @Override
      public ParetoComparator<T> compareArrivalTimeRoundCostAndOnBoardArrival() {
        return (l, r) -> compareBase(l, r) || compareArrivedOnBoard(l, r);
      }
    };
  }

  private static <T extends McStopArrival<?>> ArrivalParetoSetComparatorFactory<
    T
  > createFactoryC1AndC2(DominanceFunction c2DominanceFunction) {
    return new ArrivalParetoSetComparatorFactory<>() {
      @Override
      public ParetoComparator<T> compareArrivalTimeRoundAndCost() {
        return (l, r) -> compareBase(l, r) || c2DominanceFunction.leftDominateRight(l.c2(), r.c2());
      }

      @Override
      public ParetoComparator<T> compareArrivalTimeRoundCostAndOnBoardArrival() {
        return (
          (l, r) ->
            compareBase(l, r) ||
            compareArrivedOnBoard(l, r) ||
            c2DominanceFunction.leftDominateRight(l.c2(), r.c2())
        );
      }
    };
  }

  private static <T extends McStopArrival<?>> ArrivalParetoSetComparatorFactory<
    T
  > createFactoryRelaxC1(RelaxFunction rc1) {
    return new ArrivalParetoSetComparatorFactory<>() {
      @Override
      public ParetoComparator<T> compareArrivalTimeRoundAndCost() {
        return (l, r) -> relaxedCompareBase(rc1, l, r);
      }

      @Override
      public ParetoComparator<T> compareArrivalTimeRoundCostAndOnBoardArrival() {
        return (l, r) -> relaxedCompareBase(rc1, l, r) || compareArrivedOnBoard(l, r);
      }
    };
  }

  private static <T extends McStopArrival<?>> ArrivalParetoSetComparatorFactory<
    T
  > createFactoryRelaxC2(RelaxFunction relaxC1, DominanceFunction c2DominanceFunction) {
    return new ArrivalParetoSetComparatorFactory<>() {
      @Override
      public ParetoComparator<T> compareArrivalTimeRoundAndCost() {
        // If c2 dominates, then a slack is added to arrival-time and cost (c1)
        return (l, r) ->
          c2DominanceFunction.leftDominateRight(l.c2(), r.c2())
            ? relaxedCompareBase(relaxC1, l, r)
            : compareBase(l, r);
      }

      @Override
      public ParetoComparator<T> compareArrivalTimeRoundCostAndOnBoardArrival() {
        return (l, r) ->
          (c2DominanceFunction.leftDominateRight(l.c2(), r.c2())
            ? relaxedCompareBase(relaxC1, l, r)
            : compareBase(l, r)) ||
          compareArrivedOnBoard(l, r);
      }
    };
  }

  /**
   * Compare arrivalTime, paretoRound and c1.
   */
  static boolean compareBase(McStopArrival<?> l, McStopArrival<?> r) {
    // This is important with respect to performance. Using the short-circuit logical OR(||) is
    // faster than bitwise inclusive OR(|) (even between boolean expressions)
    return (
      l.arrivalTime() < r.arrivalTime() || l.paretoRound() < r.paretoRound() || l.c1() < r.c1()
    );
  }

  /**
   * Compare arrivalTime, paretoRound and c1, relaxing arrivalTime and c1.
   */
  static boolean relaxedCompareBase(
    final RelaxFunction relaxC1,
    McStopArrival<?> l,
    McStopArrival<?> r
  ) {
    return (
      l.arrivalTime() < r.arrivalTime() ||
      l.paretoRound() < r.paretoRound() ||
      l.c1() < relaxC1.relax(r.c1())
    );
  }

  /**
   * Compare arrivedOnBoard. On-board arrival dominate arrive by transfer(foot) since
   * you can continue on foot; hence has more options.
   */
  static boolean compareArrivedOnBoard(McStopArrival<?> l, McStopArrival<?> r) {
    return l.arrivedOnBoard() && !r.arrivedOnBoard();
  }
}
