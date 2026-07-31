package org.opentripplanner.ext.updater.trip.unified.model.change;

import java.util.ArrayList;
import java.util.List;
import org.opentripplanner.transit.model.timetable.RealTimeTripTimesBuilder;
import org.opentripplanner.updater.spi.UpdateSuccess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility methods for working with lists of resolved stop time updates, shared by
 * {@link org.opentripplanner.ext.updater.trip.unified.model.change.TripAddition}, {@link org.opentripplanner.ext.updater.trip.unified.service.TripCreator TripCreator} and
 * {@link org.opentripplanner.ext.updater.trip.unified.service.TripModifier TripModifier}.
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
   * Unknown stops in FAIL mode are caught by {@link TripCreation} beforehand,
   * so this method only needs to handle IGNORE mode filtering.
   */
  public static FilteredStopTimeUpdates filterUnknownStops(List<ResolvedStopTimeUpdate> updates) {
    var warnings = new ArrayList<UpdateSuccess.WarningType>();

    // Filter unknown stops (IGNORE mode)
    var filteredUpdates = new ArrayList<ResolvedStopTimeUpdate>();
    for (var stopUpdate : updates) {
      if (stopUpdate.referencedStop() != null) {
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
   * Apply the call data to a trip times builder. Trip-level attributes are applied separately by the
   * change itself.
   *
   * @param builder The builder to apply updates to
   * @param stopTimeUpdates The resolved stop time updates to apply
   */
  public static void applyRealTimeUpdates(
    RealTimeTripTimesBuilder builder,
    List<ResolvedStopTimeUpdate> stopTimeUpdates
  ) {
    for (int i = 0; i < stopTimeUpdates.size(); i++) {
      stopTimeUpdates.get(i).applyTo(builder, i);
    }
  }
}
