package org.opentripplanner.ext.updater.trip.unified.model;

/**
 * The number a call carries in the static feed - the GTFS {@code stop_sequence}. It is only
 * required to increase along the trip, so it is <em>not</em> a position in the pattern: which
 * call it names has to be looked up in the numbering of the trip itself, through
 * {@code TripTimes#stopPositionForGtfsSequence(int)}. Wrapping the number keeps it apart from
 * the pattern indexes it travels alongside, which no compiler can tell from it when both are
 * ints.
 */
public final class StopSequence {

  private final int value;

  private StopSequence(int value) {
    this.value = value;
  }

  /**
   * @throws IllegalArgumentException for a negative number. GTFS declares the field unsigned, so
   *                                  a negative value is the protobuf uint32 read overflowing the
   *                                  Java int - a number no feed's static side carries, which no
   *                                  lookup could therefore ever resolve.
   */
  public static StopSequence of(int value) {
    if (value < 0) {
      throw new IllegalArgumentException("A GTFS stop_sequence is never negative: " + value);
    }
    return new StopSequence(value);
  }

  public int value() {
    return value;
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof StopSequence other && value == other.value;
  }

  @Override
  public int hashCode() {
    return Integer.hashCode(value);
  }

  @Override
  public String toString() {
    return Integer.toString(value);
  }
}
