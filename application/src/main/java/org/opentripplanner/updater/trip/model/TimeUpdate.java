package org.opentripplanner.updater.trip.model;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;
import javax.annotation.Nullable;

/**
 * A unified representation of a time update that handles both:
 * <ul>
 *   <li>GTFS-RT's delay-based times (delay relative to scheduled time)</li>
 *   <li>SIRI's explicit absolute times</li>
 * </ul>
 * <p>
 * This class implements {@link ParsedTimeUpdate} and represents an already-resolved
 * time update (as opposed to {@link DeferredTimeUpdate} which requires service date resolution).
 */
public final class TimeUpdate implements ParsedTimeUpdate {

  @Nullable
  private final Integer delaySeconds;

  @Nullable
  private final Integer absoluteTimeSecondsSinceMidnight;

  @Nullable
  private final Integer scheduledTimeSecondsSinceMidnight;

  /**
   * @param delaySeconds The delay in seconds relative to the scheduled time. Positive values
   *                     indicate the vehicle is late, negative values indicate it's early.
   * @param absoluteTimeSecondsSinceMidnight The absolute time in seconds since midnight (service day).
   * @param scheduledTimeSecondsSinceMidnight The scheduled time in seconds since midnight, used
   *                                          to calculate delay when only absolute time is provided.
   */
  public TimeUpdate(
    @Nullable Integer delaySeconds,
    @Nullable Integer absoluteTimeSecondsSinceMidnight,
    @Nullable Integer scheduledTimeSecondsSinceMidnight
  ) {
    this.delaySeconds = delaySeconds;
    this.absoluteTimeSecondsSinceMidnight = absoluteTimeSecondsSinceMidnight;
    this.scheduledTimeSecondsSinceMidnight = scheduledTimeSecondsSinceMidnight;
  }

  /**
   * Create a delay-based time update.
   *
   * @param delaySeconds The delay in seconds (positive = late, negative = early)
   */
  public static TimeUpdate ofDelay(int delaySeconds) {
    return new TimeUpdate(delaySeconds, null, null);
  }

  /**
   * Create an absolute time update.
   *
   * @param absoluteTime The absolute time in seconds since midnight
   * @param scheduledTime The scheduled time in seconds since midnight (optional, for delay calculation)
   */
  public static TimeUpdate ofAbsolute(int absoluteTime, @Nullable Integer scheduledTime) {
    return new TimeUpdate(null, absoluteTime, scheduledTime);
  }

  @Nullable
  public Integer delaySeconds() {
    return delaySeconds;
  }

  @Nullable
  public Integer absoluteTimeSecondsSinceMidnight() {
    return absoluteTimeSecondsSinceMidnight;
  }

  @Nullable
  public Integer scheduledTimeSecondsSinceMidnight() {
    return scheduledTimeSecondsSinceMidnight;
  }

  /**
   * Returns true if this update contains a delay value.
   */
  public boolean hasDelay() {
    return delaySeconds != null;
  }

  /**
   * Returns true if this update contains an absolute time value.
   */
  public boolean hasAbsoluteTime() {
    return absoluteTimeSecondsSinceMidnight != null;
  }

  /**
   * Resolve the actual time in seconds since midnight.
   *
   * @param scheduledTime The scheduled time to use if delay-based
   * @return The resolved time in seconds since midnight
   */
  public int resolveTime(int scheduledTime) {
    if (absoluteTimeSecondsSinceMidnight != null) {
      return absoluteTimeSecondsSinceMidnight;
    }
    if (delaySeconds != null) {
      return scheduledTime + delaySeconds;
    }
    return scheduledTime;
  }

  /**
   * Resolve the delay in seconds relative to scheduled time.
   *
   * @param scheduledTime The scheduled time to use for calculation
   * @return The delay in seconds (positive = late, negative = early)
   */
  public int resolveDelay(int scheduledTime) {
    if (delaySeconds != null) {
      return delaySeconds;
    }
    if (absoluteTimeSecondsSinceMidnight != null) {
      return absoluteTimeSecondsSinceMidnight - scheduledTime;
    }
    return 0;
  }

  /**
   * Implementation of {@link ParsedTimeUpdate#resolve(LocalDate, ZoneId)}.
   * Since TimeUpdate is already resolved, this simply returns itself.
   *
   * @param serviceDate ignored (already resolved)
   * @param timeZone ignored (already resolved)
   * @return this TimeUpdate
   */
  @Override
  public TimeUpdate resolve(LocalDate serviceDate, ZoneId timeZone) {
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    TimeUpdate that = (TimeUpdate) o;
    return (
      Objects.equals(delaySeconds, that.delaySeconds) &&
      Objects.equals(absoluteTimeSecondsSinceMidnight, that.absoluteTimeSecondsSinceMidnight) &&
      Objects.equals(scheduledTimeSecondsSinceMidnight, that.scheduledTimeSecondsSinceMidnight)
    );
  }

  @Override
  public int hashCode() {
    return Objects.hash(
      delaySeconds,
      absoluteTimeSecondsSinceMidnight,
      scheduledTimeSecondsSinceMidnight
    );
  }

  @Override
  public String toString() {
    return (
      "TimeUpdate{" +
      "delaySeconds=" +
      delaySeconds +
      ", absoluteTimeSecondsSinceMidnight=" +
      absoluteTimeSecondsSinceMidnight +
      ", scheduledTimeSecondsSinceMidnight=" +
      scheduledTimeSecondsSinceMidnight +
      '}'
    );
  }
}
