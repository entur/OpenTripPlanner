package org.opentripplanner.updater.trip.handlers;

import java.util.List;
import org.opentripplanner.transit.model.timetable.RealTimeTripTimesBuilder;
import org.opentripplanner.updater.trip.model.ResolvedStopTimeUpdate;
import org.opentripplanner.updater.trip.model.TripCreationInfo;

/**
 * Utility methods shared between trip update handlers.
 */
public final class HandlerUtils {

  private HandlerUtils() {}

  /**
   * Apply real-time updates to a trip times builder.
   *
   * @param tripCreationInfo Optional trip creation info (for trip-level headsign)
   * @param builder The builder to apply updates to
   * @param stopTimeUpdates The resolved stop time updates to apply
   */
  public static void applyRealTimeUpdates(
    TripCreationInfo tripCreationInfo,
    RealTimeTripTimesBuilder builder,
    List<ResolvedStopTimeUpdate> stopTimeUpdates
  ) {
    // Apply trip-level headsign from trip creation info
    if (tripCreationInfo != null && tripCreationInfo.headsign() != null) {
      builder.withTripHeadsign(tripCreationInfo.headsign());
    }

    for (int i = 0; i < stopTimeUpdates.size(); i++) {
      stopTimeUpdates.get(i).applyTo(builder, i);
    }
  }
}
