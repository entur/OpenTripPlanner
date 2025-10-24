package org.opentripplanner.utils.collection;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.OptionalInt;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class BidirectionalSearchTest {

  private static final int NOT_FOUND = -1;
  private static final int[] O = {};
  private static final int[] A0 = { 0 };
  private static final int[] A1 = { 1 };
  private static final int[] A00 = { 0, 0 };
  private static final int[] A01 = { 0, 1 };
  private static final int[] A10 = { 1, 0 };
  private static final int[] A11 = { 1, 1 };
  private static final int[] A000 = { 0, 0, 0 };
  private static final int[] A100 = { 1, 0, 0 };
  private static final int[] A001 = { 0, 0, 1 };
  private static final int[] A1001 = { 1, 0, 0, 1 };

  static List<Arguments> findNearestTestCases() {
    return List.of(
      // find the one existing element
      Arguments.of(0, A1, 0, 0, 1),
      Arguments.of(0, A10, 0, 0, 2),
      Arguments.of(0, A10, 1, 0, 2),
      Arguments.of(1, A01, 0, 0, 2),
      Arguments.of(1, A01, 1, 0, 2),
      Arguments.of(0, A10, 1, 0, 2),
      // find first match
      Arguments.of(0, A1001, 0, 0, 4),
      Arguments.of(0, A1001, 1, 0, 4),
      Arguments.of(3, A1001, 2, 0, 4),
      Arguments.of(3, A1001, 3, 0, 4)
    );
  }

  @ParameterizedTest
  @MethodSource("findNearestTestCases")
  void testFindNearest(int expected, int[] a, int start, int lowerBound, int upperBound) {
    int distanceToTarget = Math.abs(start - expected);

    if (distanceToTarget > 0) {
      assertEquals(
        OptionalInt.empty(),
        BidirectionalSearch.findNearest(
          start,
          lowerBound,
          upperBound,
          distanceToTarget - 1,
          i -> a[i] == 1
        )
      );
    }
    assertEquals(
      OptionalInt.of(expected),
      BidirectionalSearch.findNearest(
        start,
        lowerBound,
        upperBound,
        distanceToTarget,
        i -> a[i] == 1
      )
    );
    assertEquals(
      OptionalInt.of(expected),
      BidirectionalSearch.findNearest(
        start,
        lowerBound,
        upperBound,
        distanceToTarget + 1,
        i -> a[i] == 1
      )
    );
  }

  static List<Arguments> findNearestNotFoundTestCases() {
    return List.of(
      Arguments.of(O, 0, 0, 0, "Empty array"),
      Arguments.of(A0, 0, 0, 1, "Empty array"),
      Arguments.of(A00, 0, 0, 2, "Empty array"),
      Arguments.of(A000, 0, 0, 3, "Empty array"),
      // start index is outside range above
      Arguments.of(O, 1, 0, 0, "Empty array, startIndex is above"),
      Arguments.of(A1, 1, 0, 1, "One element, startIndex is above"),
      Arguments.of(A11, 1, 0, 1, "Two elements, startIndex is above upper bound"),
      // start index is outside range below
      Arguments.of(O, -1, 0, 0, "Empty array, startIndex is below"),
      Arguments.of(A1, -1, 0, 1, "One element, startIndex is below"),
      Arguments.of(A11, 0, 1, 2, "Two elements, startIndex is below lower bound"),
      // start index not found in range
      Arguments.of(A1, 0, 0, 0, "One element, upper bound == lower bound"),
      Arguments.of(A11, 0, 0, 0, "Two elements, upper bound == lower bound"),
      Arguments.of(A11, 1, 1, 1, "Two elements, upper bound == lower bound"),
      Arguments.of(A000, 0, 0, 3, "3 elements, element do not exist, index=0"),
      Arguments.of(A000, 1, 0, 3, "3 elements, element do not exist, index=1"),
      Arguments.of(A000, 2, 0, 3, "3 elements, element do not exist, index=2"),
      Arguments.of(A100, 1, 1, 3, "3 elements, element exists(#0) outside range[1..2]"),
      Arguments.of(A001, 1, 0, 2, "3 elements, element exists(#2) outside range[0..1]"),
      Arguments.of(A1001, 1, 1, 3, "4 elements, element exists(#0 & #3) outside range[1..2]")
    );
  }

  @ParameterizedTest
  @MethodSource("findNearestNotFoundTestCases")
  void testFindNearestNotFound(
    int[] a,
    int start,
    int lowerBound,
    int upperBound,
    String description
  ) {
    int maxLimit = a.length;
    assertEquals(
      OptionalInt.empty(),
      BidirectionalSearch.findNearest(start, lowerBound, upperBound, maxLimit, i -> a[i] == 1),
      description
    );
  }
}
