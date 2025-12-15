package org.opentripplanner.ext.fares.service.gtfs.v2;

import java.time.ZonedDateTime;
import java.util.Collection;
import org.opentripplanner.core.model.id.FeedScopedId;
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

  ///
  /// Check if a leg matches the timeframe restrictions of a fare rule.
  ///
  /// A leg matches if:
  /// - The rule has no timeframe restrictions (empty), OR
  /// - The leg's departure time falls within at least one of the rule's from timeframes, AND
  /// - The leg's arrival time falls within at least one of the rule's to timeframes
  static boolean matchesTimeframes(TransitLeg leg, FareLegRule rule) {
    var fromTimeframes = rule.fromTimeframes();
    var toTimeframes = rule.toTimeframes();

    // If both are empty, there are no timeframe restrictions
    if (rule.fromTimeframes().isEmpty() && toTimeframes.isEmpty()) {
      return true;
    }

    // Check from timeframes (departure time)
    var fromMatches =
      fromTimeframes.isEmpty() ||
      matchesAnyTimeframe(leg.start().scheduledTime(), leg.trip().getServiceId(), fromTimeframes);

    var toMatches =
      toTimeframes.isEmpty() ||
      matchesAnyTimeframe(leg.end().scheduledTime(), leg.trip().getServiceId(), toTimeframes);

    return fromMatches && toMatches;
  }

  /**
   * Check if a time matches any of the provided timeframes.
   */
  private static boolean matchesAnyTimeframe(
    ZonedDateTime time,
    FeedScopedId serviceId,
    Collection<Timeframe> timeframes
  ) {
    return timeframes.stream().anyMatch(tf -> matchesTimeframe(time, serviceId, tf));
  }

  /**
   * Check if a time falls within a specific timeframe.
   */
  private static boolean matchesTimeframe(
    ZonedDateTime time,
    FeedScopedId serviceId,
    Timeframe timeframe
  ) {
    var localTime = time.toLocalTime();
    // For now, only check time ranges
    var start = timeframe.startTime();
    var end = timeframe.endTime();

    if (!timeframe.serviceId().equals(serviceId)) {
      return false;
    }

    // Handle cases where timeframe crosses midnight
    if (end.isBefore(start)) {
      // Timeframe crosses midnight, e.g., 22:00 to 02:00
      return !localTime.isBefore(start) || !localTime.isAfter(end);
    } else {
      // Normal case: start <= time <= end
      return !localTime.isBefore(start) && !localTime.isAfter(end);
    }
  }
}
