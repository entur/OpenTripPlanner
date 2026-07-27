package org.opentripplanner.updater.trip.model;

import javax.annotation.Nullable;

/**
 * A time update expressed as an explicit time, as used by SIRI and by GTFS-RT feeds that report a
 * time rather than a delay.
 *
 * @param time the real-time value in seconds since midnight of the service day.
 * @param aimedTime the scheduled time in seconds since midnight of the service day, as reported by
 *                  the feed itself, or {@code null} if the feed did not report one. This is needed
 *                  for trips that have no scheduled timetable to compare against - added and
 *                  modified trips - and for fuzzy trip matching.
 */
public record AbsoluteTimeUpdate(int time, @Nullable Integer aimedTime) implements TimeUpdate {
  @Override
  public int resolveTime(int scheduledTime) {
    return time;
  }

  /**
   * The aimed time reported by the feed, falling back to the real-time value when the feed
   * reported none. Used when building the stop pattern of an added or modified trip, where there
   * is no scheduled timetable to fall back to.
   */
  public int aimedTimeOrActual() {
    return aimedTime != null && aimedTime > 0 ? aimedTime : time;
  }
}
