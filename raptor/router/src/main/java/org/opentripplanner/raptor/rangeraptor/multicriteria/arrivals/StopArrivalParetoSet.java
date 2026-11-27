package org.opentripplanner.raptor.rangeraptor.multicriteria.arrivals;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.opentripplanner.raptor.rangeraptor.multicriteria.arrivals.stop.ArrivalCriteriaComparator;
import org.opentripplanner.raptor.rangeraptor.multicriteria.arrivals.stop.McStopArrival;
import org.opentripplanner.raptor.spi.RaptorTripSchedule;
import org.opentripplanner.raptor.util.paretoset.ParetoSet;
import org.opentripplanner.raptor.util.paretoset.ParetoSetEventListener;

/**
 * The pareto-optimal arrivals at a stop. An arrival is accepted, rejected or dropped exactly as by
 * a {@link ParetoSet}: the elements are kept in the order they are added, the first element that
 * dominates a new arrival rejects it, and the listener gets the same events in the same order.
 * <p>
 * The arrivals at a stop are created at different points of the search, so they are spread across
 * the heap, and reading the criteria from each of them to compare it with a new arrival is slow.
 * This set keeps a copy of the criteria of each element in one array, and compares a new arrival
 * with the copies.
 *
 * @param <T> The TripSchedule type defined by the user of the raptor API.
 */
final class StopArrivalParetoSet<T extends RaptorTripSchedule> {

  /** The criteria copied for each element: arrival-time, round, c1 and c2. */
  private static final int N_CRITERIA = 4;

  private static final int INITIAL_CAPACITY = 8;

  private final ArrivalCriteriaComparator comparator;
  private final boolean compareArrivedOnBoard;

  @Nullable
  private final ParetoSetEventListener<? super McStopArrival<T>> eventListener;

  @SuppressWarnings("unchecked")
  private McStopArrival<T>[] elements = (McStopArrival<T>[]) new McStopArrival[INITIAL_CAPACITY];

  /** The criteria of element {@code i} start at index {@code i * N_CRITERIA}. */
  private int[] criteria = new int[INITIAL_CAPACITY * N_CRITERIA];

  private int size = 0;
  private int marker = 0;

  /**
   * The last element that dominated a new arrival, and a copy of its criteria. It is kept when the
   * element is dropped from the set.
   */
  private McStopArrival<T> goodElement = null;
  private int goodArrivalTime;
  private int goodRound;
  private int goodC1;
  private int goodC2;

  /**
   * @param comparator            compares the arrival-time, round, c1 and c2 of two arrivals.
   * @param compareArrivedOnBoard if {@code true}, an arrival that arrived on-board is also better
   *                              than one that did not.
   * @param eventListener         is notified when an arrival is accepted, dropped or rejected.
   */
  StopArrivalParetoSet(
    ArrivalCriteriaComparator comparator,
    boolean compareArrivedOnBoard,
    @Nullable ParetoSetEventListener<? super McStopArrival<T>> eventListener
  ) {
    this.comparator = comparator;
    this.compareArrivedOnBoard = compareArrivedOnBoard;
    this.eventListener = eventListener;
  }

  int size() {
    return size;
  }

  boolean isEmpty() {
    return size == 0;
  }

  Stream<McStopArrival<T>> stream() {
    return Arrays.stream(elements, 0, size);
  }

  /**
   * This is used for logging and tuning purposes: the statistics show what a good value for the
   * initial size is.
   */
  int internalArrayLength() {
    return elements.length;
  }

  boolean hasElementsAfterMarker() {
    return marker != size;
  }

  /**
   * List all elements added after the marker. The iterators are NOT thread-safe, and the set must
   * not be modified while one is used.
   */
  Iterable<McStopArrival<T>> elementsAfterMarker() {
    final int startInclusive = marker;
    return () -> tailIterator(startInclusive);
  }

  /**
   * Move the marker after the last element in the set.
   */
  void markAtEndOfSet() {
    marker = size;
  }

  /**
   * Add the arrival if it is pareto-optimal, and drop the elements it dominates.
   *
   * @return {@code true} if the arrival is added.
   */
  boolean add(McStopArrival<T> newValue) {
    final int arrivalTime = newValue.arrivalTime();
    final int round = newValue.round();
    final int c1 = newValue.c1();
    final int c2 = newValue.c2();

    if (size == 0) {
      acceptAndAppendValue(newValue, arrivalTime, round, c1, c2);
      return true;
    }

    // With the arrived-on-board criterion, the new arrival is either better than the elements that
    // arrived on-street, or worse than the elements that arrived on-board
    final boolean onBoard = compareArrivedOnBoard && newValue.arrivedOnBoard();
    final boolean onStreet = compareArrivedOnBoard && !onBoard;

    // Quick shortcut, one element probably dominate most of the new elements
    if (
      goodElement != null &&
      goodElementIsBetter(arrivalTime, round, c1, c2, onStreet) &&
      !newValueIsBetterThanGoodElement(arrivalTime, round, c1, c2, onBoard)
    ) {
      notifyElementRejected(newValue, goodElement);
      return false;
    }

    for (int i = 0; i < size; ++i) {
      final boolean newValueIsBetter = newValueIsBetter(arrivalTime, round, c1, c2, onBoard, i);
      final boolean elementIsBetter = elementIsBetter(i, arrivalTime, round, c1, c2, onStreet);

      if (newValueIsBetter) {
        if (elementIsBetter) {
          // Mutual dominance
          continue;
        }
        removeDominatedElementsFromRestOfSetAndAddNewElement(
          newValue,
          arrivalTime,
          round,
          c1,
          c2,
          onBoard,
          onStreet,
          i
        );
        return true;
      }
      // The element dominates the new value, or the two are equal
      if (elementIsBetter) {
        setGoodElement(i);
      }
      if (eventListener != null) {
        eventListener.notifyElementRejected(newValue, elements[i]);
      }
      return false;
    }

    assertEnoughSpaceInSet();
    acceptAndAppendValue(newValue, arrivalTime, round, c1, c2);
    return true;
  }

  @Override
  public String toString() {
    return stream()
      .map(Objects::toString)
      .collect(Collectors.joining(", ", "{", "}"));
  }

  /**
   * The elements from the given index. The iterator reflects the set as it is when the iterator is
   * used, not when it is created.
   */
  private Iterator<McStopArrival<T>> tailIterator(final int startInclusive) {
    return new Iterator<>() {
      int i = startInclusive;

      @Override
      public boolean hasNext() {
        return i < size;
      }

      @Override
      public McStopArrival<T> next() {
        return elements[i++];
      }
    };
  }

  /**
   * Remove all elements dominated by the {@code newValue} starting from {@code index + 1}. The
   * element at {@code index} is dropped.
   */
  private void removeDominatedElementsFromRestOfSetAndAddNewElement(
    final McStopArrival<T> newValue,
    final int arrivalTime,
    final int round,
    final int c1,
    final int c2,
    final boolean onBoard,
    final boolean onStreet,
    final int index
  ) {
    // Let 'i' be the current element index for removal
    int i = index;
    // Let 'j' be the next element to compare
    int j = index + 1;

    notifyElementDropped(elements[i], newValue);

    while (j < size) {
      notifyElementMoved(j, i);
      // Move next element(j) forward if it is not dominated by the new value
      if (
        !newValueIsBetter(arrivalTime, round, c1, c2, onBoard, j) ||
        elementIsBetter(j, arrivalTime, round, c1, c2, onStreet)
      ) {
        moveElement(j, i);
        ++i;
      } else {
        notifyElementDropped(elements[j], newValue);
      }
      // Goto the next element
      ++j;
    }
    notifyElementMoved(j, i);
    notifyElementAccepted(newValue);
    setElement(i, newValue, arrivalTime, round, c1, c2);
    size = i + 1;
  }

  /**
   * Is at least one criterion of the new arrival better than the same criterion of the element at
   * the given index?
   */
  private boolean newValueIsBetter(
    int arrivalTime,
    int round,
    int c1,
    int c2,
    boolean onBoard,
    int index
  ) {
    final int k = index * N_CRITERIA;
    return (
      comparator.leftDominanceExist(
        arrivalTime,
        round,
        c1,
        c2,
        criteria[k],
        criteria[k + 1],
        criteria[k + 2],
        criteria[k + 3]
      ) ||
      (onBoard && !elements[index].arrivedOnBoard())
    );
  }

  /**
   * Is at least one criterion of the element at the given index better than the same criterion of
   * the new arrival?
   */
  private boolean elementIsBetter(
    int index,
    int arrivalTime,
    int round,
    int c1,
    int c2,
    boolean onStreet
  ) {
    final int k = index * N_CRITERIA;
    return (
      comparator.leftDominanceExist(
        criteria[k],
        criteria[k + 1],
        criteria[k + 2],
        criteria[k + 3],
        arrivalTime,
        round,
        c1,
        c2
      ) ||
      (onStreet && elements[index].arrivedOnBoard())
    );
  }

  private boolean goodElementIsBetter(
    int arrivalTime,
    int round,
    int c1,
    int c2,
    boolean onStreet
  ) {
    return (
      comparator.leftDominanceExist(
        goodArrivalTime,
        goodRound,
        goodC1,
        goodC2,
        arrivalTime,
        round,
        c1,
        c2
      ) ||
      (onStreet && goodElement.arrivedOnBoard())
    );
  }

  private boolean newValueIsBetterThanGoodElement(
    int arrivalTime,
    int round,
    int c1,
    int c2,
    boolean onBoard
  ) {
    return (
      comparator.leftDominanceExist(
        arrivalTime,
        round,
        c1,
        c2,
        goodArrivalTime,
        goodRound,
        goodC1,
        goodC2
      ) ||
      (onBoard && !goodElement.arrivedOnBoard())
    );
  }

  private void setGoodElement(int index) {
    final int k = index * N_CRITERIA;
    goodElement = elements[index];
    goodArrivalTime = criteria[k];
    goodRound = criteria[k + 1];
    goodC1 = criteria[k + 2];
    goodC2 = criteria[k + 3];
  }

  private void acceptAndAppendValue(
    McStopArrival<T> newValue,
    int arrivalTime,
    int round,
    int c1,
    int c2
  ) {
    notifyElementAccepted(newValue);
    setElement(size, newValue, arrivalTime, round, c1, c2);
    ++size;
  }

  private void setElement(
    int index,
    McStopArrival<T> value,
    int arrivalTime,
    int round,
    int c1,
    int c2
  ) {
    final int k = index * N_CRITERIA;
    elements[index] = value;
    criteria[k] = arrivalTime;
    criteria[k + 1] = round;
    criteria[k + 2] = c1;
    criteria[k + 3] = c2;
  }

  private void moveElement(int fromIndex, int toIndex) {
    final int from = fromIndex * N_CRITERIA;
    final int to = toIndex * N_CRITERIA;
    elements[toIndex] = elements[fromIndex];
    criteria[to] = criteria[from];
    criteria[to + 1] = criteria[from + 1];
    criteria[to + 2] = criteria[from + 2];
    criteria[to + 3] = criteria[from + 3];
  }

  private void assertEnoughSpaceInSet() {
    if (size == elements.length) {
      elements = Arrays.copyOf(elements, elements.length * 2);
      criteria = Arrays.copyOf(criteria, criteria.length * 2);
    }
  }

  private void notifyElementMoved(int fromIndex, int toIndex) {
    if (fromIndex == marker) {
      marker = toIndex;
    }
  }

  private void notifyElementAccepted(McStopArrival<T> newElement) {
    if (eventListener != null) {
      eventListener.notifyElementAccepted(newElement);
    }
  }

  private void notifyElementDropped(McStopArrival<T> element, McStopArrival<T> droppedByElement) {
    if (eventListener != null) {
      eventListener.notifyElementDropped(element, droppedByElement);
    }
  }

  private void notifyElementRejected(McStopArrival<T> element, McStopArrival<T> rejectByElement) {
    if (eventListener != null) {
      eventListener.notifyElementRejected(element, rejectByElement);
    }
  }
}
