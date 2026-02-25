package org.opentripplanner.updater.trip.model;

import java.util.List;
import javax.annotation.Nullable;

/**
 * Common interface for updates to existing scheduled trips.
 * <p>
 * Used by {@link org.opentripplanner.updater.trip.ExistingTripResolver} for both
 * UPDATE_EXISTING ({@link ParsedUpdateExisting}) and MODIFY_TRIP ({@link ParsedModifyTrip}).
 */
public sealed interface ParsedExistingTripUpdate
  extends ParsedTripUpdate
  permits ParsedUpdateExisting, ParsedModifyTrip {
  List<ParsedStopTimeUpdate> stopTimeUpdates();

  TripUpdateOptions options();

  /**
   * Returns true if any stop time update has an explicit stop sequence number.
   * GTFS-RT updates typically have stop sequences, while SIRI updates do not.
   */
  default boolean hasStopSequences() {
    return stopTimeUpdates()
      .stream()
      .anyMatch(u -> u.stopSequence() != null);
  }

  /**
   * Returns the trip creation info, if any.
   * Only {@link ParsedModifyTrip} may carry trip creation info.
   */
  @Nullable
  default TripCreationInfo tripCreationInfo() {
    return null;
  }
}
