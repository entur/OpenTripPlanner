package org.opentripplanner.raptor.rangeraptor.multicriteria.arrivals.stop;

/**
 * Compares the criteria of two stop arrivals for pareto dominance: the arrival-time, the round,
 * {@code c1} and {@code c2}. The criteria are passed as values, so two arrivals can be compared
 * without reading the criteria from the arrivals.
 */
@FunctionalInterface
public interface ArrivalCriteriaComparator {
  /**
   * Returns {@code true} if at least one criterion of the left arrival is better than the
   * corresponding criterion of the right arrival.
   */
  boolean leftDominanceExist(
    int leftArrivalTime,
    int leftRound,
    int leftC1,
    int leftC2,
    int rightArrivalTime,
    int rightRound,
    int rightC1,
    int rightC2
  );
}
