package org.opentripplanner.updater.trip;

import java.util.ArrayList;
import java.util.List;
import org.opentripplanner.transit.model.timetable.RealTimeTripTimesBuilder;
import org.opentripplanner.updater.spi.UpdateSuccess;
import org.opentripplanner.updater.trip.model.ResolvedStopTimeUpdate;
import org.opentripplanner.updater.trip.model.TripCreationInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility methods for working with lists of resolved stop time updates, shared by
 * {@link TripCreator}, {@link AddedTripReviser} and {@link TripModifier}.
 */
public final class StopTimeUpdates {

  private static final Logger LOG = LoggerFactory.getLogger(StopTimeUpdates.class);

  private StopTimeUpdates() {}

  /**
   * Result of filtering stop time updates.
   */
  public record FilteredStopTimeUpdates(
    List<ResolvedStopTimeUpdate> updates,
    List<UpdateSuccess.WarningType> warnings
  ) {}

  /**
   * Filter stop time updates to remove unknown stops.
   * Unknown stops in FAIL mode are caught by the {@link AddNewTripValidator} beforehand,
   * so this method only needs to handle IGNORE mode filtering.
   */
  public static FilteredStopTimeUpdates filterUnknownStops(List<ResolvedStopTimeUpdate> updates) {
    var warnings = new ArrayList<UpdateSuccess.WarningType>();

    // Filter unknown stops (IGNORE mode)
    var filteredUpdates = new ArrayList<ResolvedStopTimeUpdate>();
    for (var stopUpdate : updates) {
      if (stopUpdate.stop() != null) {
        filteredUpdates.add(stopUpdate);
      } else {
        LOG.debug("ADD_TRIP: Removing unknown stop {} from added trip", stopUpdate.stopReference());
      }
    }

    if (filteredUpdates.size() < updates.size()) {
      warnings.add(UpdateSuccess.WarningType.UNKNOWN_STOPS_REMOVED_FROM_ADDED_TRIP);
    }

    return new FilteredStopTimeUpdates(filteredUpdates, warnings);
  }

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
