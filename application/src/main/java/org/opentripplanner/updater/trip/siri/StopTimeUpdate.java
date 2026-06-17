package org.opentripplanner.updater.trip.siri;

/**
 * Represents a real-time SIRI update for a single stop within a trip, encapsulating both scheduled
 * and real-time arrival and departure times. This class provides the means to calculate arrival
 * and departure delays based on the difference between scheduled and real-time times.
 *
 * A value of {@code -1} is used to indicate that a real-time time is missing or unavailable.
 * When real-time data is absent, the scheduled time is used as a fallback. For the first stop
 * in a trip, if the real-time arrival time is missing, the real-time departure time is used
 * as a fallback before resorting to the scheduled arrival time. Similarly, for the last stop,
 * if the real-time departure time is missing, the real-time arrival time is used before
 * falling back to the scheduled departure time.
 */
class StopTimeUpdate {

  private final int scheduledArrivalTime;
  private final int realTimeArrivalTime;
  private final int scheduledDepartureTime;
  private final int realTimeDepartureTime;
  private final boolean firstStop;
  private final boolean lastStop;

  StopTimeUpdate(
    int scheduledArrivalTime,
    int realTimeArrivalTime,
    int scheduledDepartureTime,
    int realTimeDepartureTime,
    boolean firstStop,
    boolean lastStop
  ) {
    this.scheduledArrivalTime = scheduledArrivalTime;
    this.realTimeArrivalTime = realTimeArrivalTime;
    this.scheduledDepartureTime = scheduledDepartureTime;
    this.realTimeDepartureTime = realTimeDepartureTime;
    this.firstStop = firstStop;
    this.lastStop = lastStop;
  }

  public boolean hasRealTimeUpdate() {
    return realTimeArrivalTime != -1 || realTimeDepartureTime != -1;
  }

  int getArrivalDelay() {
    return getArrivalTime() - scheduledArrivalTime;
  }

  int getDepartureDelay() {
    return getDepartureTime() - scheduledDepartureTime;
  }

  private int getArrivalTime() {
    return firstStop
      ? handleMissingRealtime(realTimeArrivalTime, realTimeDepartureTime, scheduledArrivalTime)
      : handleMissingRealtime(realTimeArrivalTime, scheduledArrivalTime);
  }

  private int getDepartureTime() {
    return lastStop
      ? handleMissingRealtime(realTimeDepartureTime, realTimeArrivalTime, scheduledDepartureTime)
      : handleMissingRealtime(realTimeDepartureTime, scheduledDepartureTime);
  }

  /**
   * Loop through all passed times, return the first non-negative one or the last one
   */
  private static int handleMissingRealtime(int... times) {
    if (times.length == 0) {
      throw new IllegalArgumentException("Need at least one value");
    }

    int time = -1;
    for (int t : times) {
      time = t;
      if (time >= 0) {
        break;
      }
    }

    return time;
  }
}
