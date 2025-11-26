package org.opentripplanner.ext.fares.service.gtfs.v2;

import java.time.LocalTime;
import java.util.Collection;
import org.opentripplanner.ext.fares.model.FareLegRule;
import org.opentripplanner.ext.fares.model.Timeframe;
import org.opentripplanner.model.plan.TransitLeg;

/**
 * Matches based on the semantics of the GTFS fares V2 timeframes.
 * <p>
 * Timeframes define time-of-day restrictions for fare rules, with start and end times
 * and associated service IDs.
 */
class TimeframeMatcher {

  /**
   * Check if a leg matches the timeframe restrictions of a fare rule.
   * <p>
   * A leg matches if:
   * - The rule has no timeframe restrictions (null or empty), OR
   * - The leg's departure time falls within at least one of the rule's from timeframes, AND
   * - The leg's arrival time falls within at least one of the rule's to timeframes
   */
  boolean matchesTimeframes(TransitLeg leg, FareLegRule rule) {
    var fromTimeframes = rule.fromTimeframes();
    var toTimeframes = rule.toTimeframes();

    // If both are empty, there are no timeframe restrictions
    if (rule.fromTimeframes().isEmpty() && rule.toTimeframes().isEmpty()) {
      return true;
    }

    // Check from timeframes (departure time)
    boolean fromMatches =
      fromTimeframes.isEmpty() ||
      matchesAnyTimeframe(leg.startTime().toLocalTime(), fromTimeframes);

    // Check to timeframes (arrival time)
    boolean toMatches =
      toTimeframes.isEmpty() || matchesAnyTimeframe(leg.endTime().toLocalTime(), toTimeframes);

    return fromMatches && toMatches;
  }

  /**
   * Check if a time matches any of the provided timeframes.
   */
  private boolean matchesAnyTimeframe(LocalTime time, Collection<Timeframe> timeframes) {
    return timeframes.stream().anyMatch(tf -> matchesTimeframe(time, tf));
  }

  /**
   * Check if a time falls within a specific timeframe.
   */
  private boolean matchesTimeframe(LocalTime time, Timeframe timeframe) {
    // For now, only check time ranges
    var start = timeframe.startTime();
    var end = timeframe.endTime();

    // Handle cases where timeframe crosses midnight
    if (end.isBefore(start)) {
      // Timeframe crosses midnight, e.g., 22:00 to 02:00
      return !time.isBefore(start) || !time.isAfter(end);
    } else {
      // Normal case: start <= time <= end
      return !time.isBefore(start) && !time.isAfter(end);
    }
  }
}
