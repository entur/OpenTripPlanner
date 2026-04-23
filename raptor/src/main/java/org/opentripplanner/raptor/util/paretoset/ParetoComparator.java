package org.opentripplanner.raptor.util.paretoset;

/// Comparator used by the {@link ParetoSet} to compare to elements for dominance. There is 4
/// outcomes of a comparison between a left and right vector:
///
/// - Left dominates right `<` - At least one left criteria dominates, and no right dominance exist
/// - Right dominates left `>` - At least one right criteria dominates, and no left dominance exist
/// - Mutual dominance `||` - At least one left criteria dominates right and at least one right
///   criteria dominates left
/// - No dominance `=` - all criteria is equals or no dominance exist.
///
/// To implement the comparator you only need to implement the comparison in one direction - if dominance exist.
///
/// @param <T> The pareto set element type
///
@FunctionalInterface
public interface ParetoComparator<T> {
  /**
   * At least one of the left criteria dominates one of the corresponding right criteria.
   */
  boolean leftDominanceExist(T left, T right);

  default boolean dominanceExist(T left, T right) {
    return leftDominanceExist(left, right) || leftDominanceExist(right, left);
  }

  default ParetoDominance compare(T left, T right) {
    final boolean l = leftDominanceExist(left, right);
    final boolean r = leftDominanceExist(right, left);
    return ParetoDominance.of(l, r);
  }
}
