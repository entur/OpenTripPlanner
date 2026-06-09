package org.opentripplanner.core.model.time;

import static java.time.LocalDate.MAX;
import static java.time.LocalDate.MIN;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import javax.annotation.Nullable;
import org.opentripplanner.utils.time.ServiceDateUtils;

/**
 * Value object representing a service date interval from a starting date until an end date.
 * <p>
 * Internally the interval is always stored as [startInclusive, endExclusive). Use the factory
 * methods {@link #ofInclusiveEnd(LocalDate, LocalDate)} and
 * {@link #ofExclusiveEnd(LocalDate, LocalDate)} to construct instances according to the need, but
 * preferable use the exclusive end version unless the inclusivity comes from the input data.
 * {@link #toString()} output mirrors the convention used at construction time.
 * <p>
 * {@code null} is accepted in all factory method parameters and is treated as unbounded (equivalent
 * to {@link LocalDate#MIN} / {@link LocalDate#MAX}).
 */
public final class LocalDateInterval {

  private static final LocalDateInterval UNBOUNDED = new LocalDateInterval(MIN, MAX, true);

  private final LocalDate inclusiveStart;
  private final LocalDate exclusiveEnd;
  private final boolean inclusiveEnd;

  private LocalDateInterval(
    LocalDate inclusiveStart,
    LocalDate exclusiveEnd,
    boolean inclusiveEnd
  ) {
    this.inclusiveStart = inclusiveStart;
    this.exclusiveEnd = exclusiveEnd;
    this.inclusiveEnd = inclusiveEnd;
    if (exclusiveEnd.isBefore(inclusiveStart)) {
      throw new IllegalArgumentException(
        "Invalid interval, the end is before the start: start=" +
          inclusiveStart +
          ", endExclusive=" +
          exclusiveEnd
      );
    }
  }

  /**
   * Create an interval with an inclusive start and an inclusive end.
   *
   * @param start inclusive start, or {@code null} for unbounded start
   * @param end   inclusive end, or {@code null} for unbounded end
   */
  public static LocalDateInterval ofInclusiveEnd(
    @Nullable LocalDate start,
    @Nullable LocalDate end
  ) {
    var startInclusive = start == null ? MIN : start;
    var endInclusive = end == null ? MAX : end;
    if (endInclusive.isBefore(startInclusive)) {
      throw new IllegalArgumentException(
        "Invalid interval, the end " + end + " is before the start " + start
      );
    }
    var endExclusive = endInclusive.equals(MAX) ? MAX : endInclusive.plusDays(1);
    return new LocalDateInterval(startInclusive, endExclusive, true);
  }

  /**
   * Create an interval with an inclusive start and an exclusive end.
   *
   * @param start inclusive start, or {@code null} for unbounded start
   * @param end   exclusive end (first date outside the interval), or {@code null} for unbounded
   *              end
   */
  public static LocalDateInterval ofExclusiveEnd(
    @Nullable LocalDate start,
    @Nullable LocalDate end
  ) {
    var startInclusive = start == null ? MIN : start;
    var endExclusive = end == null ? MAX : end;
    return new LocalDateInterval(startInclusive, endExclusive, false);
  }

  /**
   * Return the interval that covers all dates (both start and end unbounded).
   */
  public static LocalDateInterval ofUnbounded() {
    return UNBOUNDED;
  }

  /**
   * Inclusive start date. Returns {@link LocalDate#MIN} when unbounded.
   */
  public LocalDate getInclusiveStart() {
    return inclusiveStart;
  }

  /**
   * Inclusive end date. Returns {@link LocalDate#MAX} when unbounded.
   */
  public LocalDate getEndInclusive() {
    return exclusiveEnd.equals(MAX) ? MAX : exclusiveEnd.minusDays(1);
  }

  /**
   * Exclusive end date (first date outside the interval). Returns {@link LocalDate#MAX} when
   * unbounded.
   */
  public LocalDate getEndExclusive() {
    return exclusiveEnd;
  }

  public boolean isUnbounded() {
    return inclusiveStart.equals(MIN) && exclusiveEnd.equals(MAX);
  }

  /**
   * Return {@code true} if the given {@code date} falls within this interval.
   */
  public boolean contains(LocalDate date) {
    return !inclusiveStart.isAfter(date) && date.isBefore(exclusiveEnd);
  }

  /**
   * Returns {@code true} if this interval and {@code other} share at least one day.
   *
   * @see #intersection(LocalDateInterval)
   */
  public boolean overlap(LocalDateInterval other) {
    return (
      inclusiveStart.isBefore(other.exclusiveEnd) && other.inclusiveStart.isBefore(exclusiveEnd)
    );
  }

  /**
   * Return a new interval containing all dates present in both intervals.
   *
   * @throws IllegalArgumentException if the two intervals do not overlap
   * @see #overlap(LocalDateInterval)
   */
  public LocalDateInterval intersection(LocalDateInterval other) {
    LocalDate newStart = ServiceDateUtils.max(inclusiveStart, other.inclusiveStart);
    LocalDate newEnd = ServiceDateUtils.min(exclusiveEnd, other.exclusiveEnd);
    if (!newStart.isBefore(newEnd)) {
      throw new IllegalArgumentException("Intervals do not overlap: " + this + " and " + other);
    }
    return new LocalDateInterval(newStart, newEnd, inclusiveEnd);
  }

  /**
   * Number of days in the interval (inclusive on both sides).
   */
  public int daysInPeriod() {
    return (int) ChronoUnit.DAYS.between(inclusiveStart, exclusiveEnd);
  }

  @Override
  public int hashCode() {
    return Objects.hash(inclusiveStart, exclusiveEnd);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    LocalDateInterval that = (LocalDateInterval) o;
    return inclusiveStart.equals(that.inclusiveStart) && exclusiveEnd.equals(that.exclusiveEnd);
  }

  @Override
  public String toString() {
    var start = ServiceDateUtils.toString(inclusiveStart);
    String end;
    if (exclusiveEnd.equals(MAX)) {
      end = ServiceDateUtils.toString(MAX);
    } else {
      end = ServiceDateUtils.toString(inclusiveEnd ? exclusiveEnd.minusDays(1) : exclusiveEnd);
    }
    var endMarker = inclusiveEnd ? "]" : ")";
    return ("[" + start + ", " + end + endMarker);
  }
}
