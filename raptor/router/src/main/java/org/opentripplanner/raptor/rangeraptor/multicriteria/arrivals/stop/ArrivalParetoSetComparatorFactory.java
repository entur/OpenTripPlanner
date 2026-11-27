package org.opentripplanner.raptor.rangeraptor.multicriteria.arrivals.stop;

import org.opentripplanner.raptor.api.model.DominanceFunction;
import org.opentripplanner.raptor.api.model.RelaxFunction;
import org.opentripplanner.raptor.util.paretoset.ParetoComparator;

/**
 * This class creates and holds two {@code ParetoComparator}s which are used in a single McRaptor
 * search.
 *
 * The creation of the two {@link ParetoComparator}s should be done in such way that the JIT
 * compiler can inline all lamdas for the best possible performance. Changes to this class should be
 * checked with the SpeedTest to avoid degration in performance.
 */
public final class ArrivalParetoSetComparatorFactory<T extends McStopArrival<?>> {

  private final ArrivalCriteriaComparator compareCriteria;
  private final ParetoComparator<T> compareRegularStopArrivals;
  private final ParetoComparator<T> compareStopArrivalsIncludingOnBoardCriteria;

  private ArrivalParetoSetComparatorFactory(ArrivalCriteriaComparator compareCriteria) {
    this.compareCriteria = compareCriteria;
    this.compareRegularStopArrivals = (l, r) ->
      compareCriteria.leftDominanceExist(
        l.arrivalTime(),
        l.round(),
        l.c1(),
        l.c2(),
        r.arrivalTime(),
        r.round(),
        r.c1(),
        r.c2()
      );
    this.compareStopArrivalsIncludingOnBoardCriteria = compareFunctionWithArrivedOnBoard(
      compareRegularStopArrivals
    );
  }

  public static <T extends McStopArrival<?>> ArrivalParetoSetComparatorFactory<T> ofCompareC1() {
    return new ArrivalParetoSetComparatorFactory<>((lt, lr, lc1, lc2, rt, rr, rc1, rc2) ->
      compareC1(lt, lr, lc1, rt, rr, rc1)
    );
  }

  public static <T extends McStopArrival<?>> ArrivalParetoSetComparatorFactory<T> ofCompareC1AndC2(
    final DominanceFunction c2DominanceFunction
  ) {
    return new ArrivalParetoSetComparatorFactory<>((lt, lr, lc1, lc2, rt, rr, rc1, rc2) ->
      compareC1AndC2(c2DominanceFunction, lt, lr, lc1, lc2, rt, rr, rc1, rc2)
    );
  }

  public static <T extends McStopArrival<?>> ArrivalParetoSetComparatorFactory<T> ofCompareC1RelaxedOnC2Dominance(
    final RelaxFunction relaxC1,
    final DominanceFunction c2DominanceFunction
  ) {
    return new ArrivalParetoSetComparatorFactory<>((lt, lr, lc1, lc2, rt, rr, rc1, rc2) ->
      compareC1RelaxedOnC2Dominance(
        relaxC1,
        c2DominanceFunction,
        lt,
        lr,
        lc1,
        lc2,
        rt,
        rr,
        rc1,
        rc2
      )
    );
  }

  /**
   * This comparator compares the criteria of two regular stop arrivals: {@code arrivalTime},
   * {@code paretoRound}, {@code c1} and, if the factory is created with it, {@code c2}. It is the
   * comparison {@link #compareArrivalTimeRoundAndCost()} does, with the criteria passed as values.
   */
  public ArrivalCriteriaComparator compareCriteria() {
    return compareCriteria;
  }

  /**
   * This comparator is used to compareFunction regular stop arrivals. It uses {@code arrivalTime},
   * {@code paretoRound} and {@code c1} to compareFunction arrivals. It does NOT include
   * {@code arrivedOnBoard}. Normally arriving on-board should give the arrival an advantage - you
   * can continue on foot, walking to the next stop. But, we only do this if it happens in the same
   * Raptor iteration and round - if it does, it is taken care of by the order which the algorithm
   * works - not by this comparator.
   */
  public ParetoComparator<T> compareArrivalTimeRoundAndCost() {
    return compareRegularStopArrivals;
  }

  /**
   * This includes {@code arrivedOnBoard} in the comparison compared with
   * {@link #compareArrivalTimeRoundAndCost()}.
   */
  public ParetoComparator<T> compareArrivalTimeRoundCostAndOnBoardArrival() {
    return compareStopArrivalsIncludingOnBoardCriteria;
  }

  /* private methods */

  private static <T extends McStopArrival<?>> ParetoComparator<T> compareFunctionWithArrivedOnBoard(
    ParetoComparator<T> compareArrivalTimeRoundAndC1
  ) {
    return (l, r) ->
      compareArrivalTimeRoundAndC1.leftDominanceExist(l, r) || compareArrivedOnBoard(l, r);
  }

  /**
   * Compare arrivalTime, paretoRound and c1.
   */
  private static boolean compareC1(int lt, int lr, int lc1, int rt, int rr, int rc1) {
    // This is important with respect to performance. Using the short-circuit logical OR(||) is
    // faster than bitwise inclusive OR(|) (even between boolean expressions)
    return lt < rt || lr < rr || lc1 < rc1;
  }

  /**
   * Compare arrivalTime, paretoRound, c1, and c21.
   */
  private static boolean compareC1AndC2(
    DominanceFunction c2Function,
    int lt,
    int lr,
    int lc1,
    int lc2,
    int rt,
    int rr,
    int rc1,
    int rc2
  ) {
    return lt < rt || lr < rr || lc1 < rc1 || c2Function.leftDominateRight(lc2, rc2);
  }

  /**
   * Compare arrivalTime, paretoRound and c1, relaxing c1.
   */
  private static boolean compareC1Relaxed(
    final RelaxFunction relaxC1,
    int lt,
    int lr,
    int lc1,
    int rt,
    int rr,
    int rc1
  ) {
    return lt < rt || lr < rr || lc1 < relaxC1.relax(rc1);
  }

  /**
   * Compare arrivalTime, paretoRound, and c1 relaxed if c2 dominates.
   */
  private static boolean compareC1RelaxedOnC2Dominance(
    final RelaxFunction relaxC1,
    DominanceFunction c2Function,
    int lt,
    int lr,
    int lc1,
    int lc2,
    int rt,
    int rr,
    int rc1,
    int rc2
  ) {
    return c2Function.leftDominateRight(lc2, rc2)
      ? compareC1Relaxed(relaxC1, lt, lr, lc1, rt, rr, rc1)
      : compareC1(lt, lr, lc1, rt, rr, rc1);
  }

  /**
   * Compare arrivedOnBoard. On-board arrival dominate arrive by transfer(foot) since you can
   * continue on foot; hence has more options.
   */
  private static <T extends McStopArrival<?>> boolean compareArrivedOnBoard(T l, T r) {
    return l.arrivedOnBoard() && !r.arrivedOnBoard();
  }
}
