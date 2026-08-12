package org.opentripplanner.ext.updater.trip.unified.model.command;

import javax.annotation.Nullable;
import org.opentripplanner.ext.updater.trip.unified.model.ServiceTime;

/**
 * A time update expressed as an explicit time, as used by SIRI and by GTFS-RT feeds that report a
 * time rather than a delay.
 *
 * @param time the real-time value.
 * @param aimedTime the scheduled time as reported by the feed itself, or {@code null} if the feed
 *                  did not report one. This is needed for trips that have no scheduled timetable
 *                  to compare against - added and modified trips - and for fuzzy trip matching.
 */
public record AbsoluteTimeUpdate(ServiceTime time, @Nullable ServiceTime aimedTime) implements
  TimeUpdate {
  @Override
  public int resolveTime(int scheduledTime) {
    return time.secondsPastMidnight();
  }

  /**
   * The aimed time reported by the feed, falling back to the real-time value when the feed
   * reported none. Whether a time was reported is decided by the field being present, never by the
   * value it carries: midnight of the service date is a time a trip can legitimately be aimed at,
   * and so is a value before it, since the origin these are measured from is noon minus twelve
   * hours. Used when building the stop pattern of an added or modified trip, where there is no
   * scheduled timetable to fall back to.
   */
  public ServiceTime aimedTimeOrActual() {
    return aimedTime != null ? aimedTime : time;
  }
}
