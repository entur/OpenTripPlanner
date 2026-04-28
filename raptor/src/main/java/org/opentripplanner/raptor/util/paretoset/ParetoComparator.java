package org.opentripplanner.raptor.util.paretoset;

/// Compares two elements in a {@link ParetoSet} for Pareto dominance.
///
/// A comparison between a `left` and `right` element can produce four outcomes:
///
/// - **Left dominates right** `≺`: at least one left criterion dominates and no right
///   criterion dominates.
/// - **Right dominates left** `≻`: at least one right criterion dominates and no left
///   criterion dominates.
/// - **Mutual dominance** `∥`: at least one left criterion dominates right and at
///   least one right criterion dominates left
/// - **No dominance** `≡`: all criteria are equal.
///
/// Implementations only need to provide one directional check in
/// {@link #leftDominanceExist(Object, Object)}.
///
/// @param <T> the Pareto set element type
///
@FunctionalInterface
public interface ParetoComparator<T> {
  /**
   * Returns {@code true} if at least one criterion in {@code left} dominates the corresponding
   * criterion in {@code right}.
   */
  boolean leftDominanceExist(T left, T right);

  /**
   * Returns {@code true} if either element dominates the other.
   */
  default boolean dominanceExist(T left, T right) {
    return leftDominanceExist(left, right) || leftDominanceExist(right, left);
  }

  /**
   * Compares {@code left} and {@code right} and returns the corresponding {@link ParetoDominance}
   * outcome.
   */
  default ParetoDominance compare(T left, T right) {
    final boolean l = leftDominanceExist(left, right);
    final boolean r = leftDominanceExist(right, left);
    return ParetoDominance.of(l, r);
  }
}
