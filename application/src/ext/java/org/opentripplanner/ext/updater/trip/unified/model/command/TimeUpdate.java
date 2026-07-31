package org.opentripplanner.ext.updater.trip.unified.model.command;

import java.time.LocalDate;
import java.time.ZoneId;
import javax.annotation.Nullable;
import org.opentripplanner.ext.updater.trip.unified.model.ServiceTime;

/**
 * A time update that no longer needs service date resolution. Real-time feeds express such an
 * update in one of two ways, one per implementation:
 * <ul>
 *   <li>{@link DelayUpdate} - a delay relative to the scheduled time, as used by GTFS-RT</li>
 *   <li>{@link AbsoluteTimeUpdate} - an explicit time, as used by SIRI</li>
 * </ul>
 */
public sealed interface TimeUpdate
  extends ParsedTimeUpdate
  permits DelayUpdate, AbsoluteTimeUpdate {
  /**
   * Create a delay-based time update.
   *
   * @param delaySeconds the delay in seconds (positive = late, negative = early)
   */
  static DelayUpdate ofDelay(int delaySeconds) {
    return new DelayUpdate(delaySeconds);
  }

  /**
   * Create an absolute time update.
   *
   * @param time the real-time value
   * @param aimedTime the scheduled time as reported by the feed, or {@code null} if it did not
   *                  report one
   */
  static AbsoluteTimeUpdate ofAbsolute(ServiceTime time, @Nullable ServiceTime aimedTime) {
    return new AbsoluteTimeUpdate(time, aimedTime);
  }

  /**
   * Resolve the actual time in seconds since midnight.
   *
   * @param scheduledTime the scheduled time to apply the delay to, ignored by updates that carry
   *                      an absolute time
   */
  int resolveTime(int scheduledTime);

  /**
   * This update is already resolved, so this returns itself.
   *
   * @param serviceDate ignored (already resolved)
   * @param timeZone ignored (already resolved)
   */
  @Override
  default TimeUpdate resolve(LocalDate serviceDate, ZoneId timeZone) {
    return this;
  }
}
