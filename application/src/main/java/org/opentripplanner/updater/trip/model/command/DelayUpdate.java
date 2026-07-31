package org.opentripplanner.updater.trip.model.command;

/**
 * A time update expressed as a delay relative to the scheduled time, as used by GTFS-RT.
 * <p>
 * Such an update only makes sense for a trip that has a scheduled timetable to apply the delay to.
 *
 * @param delaySeconds the delay in seconds relative to the scheduled time. Positive values
 *                     indicate the vehicle is late, negative values indicate it is early.
 */
public record DelayUpdate(int delaySeconds) implements TimeUpdate {
  @Override
  public int resolveTime(int scheduledTime) {
    return scheduledTime + delaySeconds;
  }
}
