package org.opentripplanner.utils.collection;

import java.util.OptionalInt;
import java.util.function.IntFunction;

/**
 * Utility class for performing bidirectional searches in index-based collections.
 * <p>
 * This class provides methods to search for elements by expanding outward from a starting
 * index in both forward and backward directions simultaneously, finding the closest match
 * to the starting point.
 */
public class BidirectionalSearch {

  /**
   * Finds the nearest index that satisfies the given test condition by searching bidirectionally
   * from a starting index.
   * <p>
   * The search expands outward from {@code startIndex} in both directions (forward and backward)
   * simultaneously, testing indices at increasing offsets until a match is found or the bounds
   * are exceeded. This ensures the closest matching index to the starting point is found.
   * <p>
   * The search pattern is: startIndex, startIndex+1, startIndex-1, startIndex+2, startIndex-2, ...
   *
   * @param startIndex the index to start searching from, if not within bounds the method return
   *                   empty.
   * @param lowerBound the lower bound of the search range (inclusive)
   * @param upperBound the upper bound of the search range (exclusive)
   * @param offsetLimit the maximum offset distance to search from startIndex. The search will
   *                    test the following elements in order:
   *                    (offsetLimit = 1,  startIndex = 3) ==> 3, 4, 2
   *                    (offsetLimit = 2,  startIndex = 3) ==> 3, 4, 2, 5, 1
   * @param test a function that tests if an index satisfies the search condition
   * @return an OptionalInt containing the nearest matching index, or empty if no match found
   */
  public static OptionalInt findNearest(
    int startIndex,
    int lowerBound,
    int upperBound,
    int offsetLimit,
    IntFunction<Boolean> test
  ) {
    if (startIndex < lowerBound || upperBound <= startIndex) {
      return OptionalInt.empty();
    }
    if (test.apply(startIndex)) {
      return OptionalInt.of(startIndex);
    }

    var keepSearchingForward = true;
    var keepSearchingBackward = true;

    for (int offeset = 0; offeset <= offsetLimit; ++offeset) {
      // Forward search
      int i = startIndex + offeset;
      keepSearchingForward = i < upperBound;

      if (keepSearchingForward) {
        if (test.apply(i)) {
          return OptionalInt.of(i);
        }
      }

      // Backward search
      i = startIndex - offeset;
      keepSearchingBackward = i >= lowerBound;

      if (keepSearchingBackward) {
        if (test.apply(i)) {
          return OptionalInt.of(i);
        }
      }

      // No more elements to search
      if (!keepSearchingForward || !keepSearchingBackward) {
        break;
      }
    }
    return OptionalInt.empty();
  }
}
