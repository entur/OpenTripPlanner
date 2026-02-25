package org.opentripplanner.updater.trip.siri;

import java.time.ZonedDateTime;
import javax.annotation.Nullable;

/**
 * Resolves arrival and departure times for SIRI-ET calls using the same fallback logic
 * as TimetableHelper. This ensures consistent handling of missing times across both
 * the old and new trip update models.
 *
 * <p>Per SIRI-ET specification:
 * <ul>
 *   <li>Expected arrival time is <strong>optional</strong> for the first stop</li>
 *   <li>Expected departure time is <strong>optional</strong> for the last stop</li>
 * </ul>
 *
 * <p>Fallback logic (matching TimetableHelper lines 78-104):
 * <ul>
 *   <li>First stop arrival: try actual/expected arrival, fallback to actual/expected departure</li>
 *   <li>Last stop departure: try actual/expected departure, fallback to actual/expected arrival</li>
 *   <li>Other stops: no cross-field fallback (only actual-to-expected fallback)</li>
 * </ul>
 *
 * <p>Time precedence (matching getAvailableTime):
 * <ul>
 *   <li>Actual (recorded) time is preferred over expected time</li>
 *   <li>Expected time is used if actual time is not available</li>
 * </ul>
 */
class SiriTimeResolver {

  /**
   * Resolved times for a stop call.
   *
   * @param arrivalTime Resolved arrival time, may be null if no times available
   * @param departureTime Resolved departure time, may be null if no times available
   */
  record ResolvedTimes(
    @Nullable ZonedDateTime arrivalTime,
    @Nullable ZonedDateTime departureTime
  ) {}

  /**
   * Resolve arrival and departure times for a call using SIRI-ET fallback rules.
   *
   * @param call The SIRI call wrapper containing time information
   * @param stopIndex The 0-based index of this stop in the trip
   * @param totalStops The total number of stops in the trip
   * @return Resolved arrival and departure times (may be null if no times available)
   */
  static ResolvedTimes resolveTimes(CallWrapper call, int stopIndex, int totalStops) {
    boolean isFirstStop = stopIndex == 0;
    boolean isLastStop = stopIndex == totalStops - 1;

    // Extract available times (matching getAvailableTime logic: actual first, then expected)
    ZonedDateTime actualArrival = call.getActualArrivalTime();
    ZonedDateTime expectedArrival = call.getExpectedArrivalTime();
    ZonedDateTime actualDeparture = call.getActualDepartureTime();
    ZonedDateTime expectedDeparture = call.getExpectedDepartureTime();

    // Resolve arrival time with fallback logic
    ZonedDateTime arrivalTime = getFirstNonNull(actualArrival, expectedArrival);
    if (isFirstStop && arrivalTime == null) {
      // First stop: fallback to departure if arrival missing (line 92-93 in TimetableHelper)
      arrivalTime = getFirstNonNull(actualDeparture, expectedDeparture);
    }

    // Resolve departure time with fallback logic
    ZonedDateTime departureTime = getFirstNonNull(actualDeparture, expectedDeparture);
    if (isLastStop && departureTime == null) {
      // Last stop: fallback to arrival if departure missing (line 99-100 in TimetableHelper)
      departureTime = getFirstNonNull(actualArrival, expectedArrival);
    }

    return new ResolvedTimes(arrivalTime, departureTime);
  }

  /**
   * Resolve aimed (scheduled) times with the same fallback logic.
   * Used to determine the scheduled reference time for TimeUpdate.
   *
   * @param call The SIRI call wrapper containing aimed time information
   * @param stopIndex The 0-based index of this stop in the trip
   * @param totalStops The total number of stops in the trip
   * @return Resolved aimed arrival and departure times
   */
  static ResolvedTimes resolveAimedTimes(CallWrapper call, int stopIndex, int totalStops) {
    boolean isFirstStop = stopIndex == 0;
    boolean isLastStop = stopIndex == totalStops - 1;

    ZonedDateTime aimedArrival = call.getAimedArrivalTime();
    ZonedDateTime aimedDeparture = call.getAimedDepartureTime();

    // Apply same fallback rules to aimed times
    if (isFirstStop && aimedArrival == null) {
      aimedArrival = aimedDeparture;
    }
    if (isLastStop && aimedDeparture == null) {
      aimedDeparture = aimedArrival;
    }

    return new ResolvedTimes(aimedArrival, aimedDeparture);
  }

  /**
   * Returns the first non-null time from the provided arguments.
   * Matches the behavior of getAvailableTime in TimetableHelper.
   *
   * @param times Times to check in order of preference
   * @return First non-null time, or null if all are null
   */
  @Nullable
  private static ZonedDateTime getFirstNonNull(ZonedDateTime... times) {
    for (ZonedDateTime time : times) {
      if (time != null) {
        return time;
      }
    }
    return null;
  }
}
