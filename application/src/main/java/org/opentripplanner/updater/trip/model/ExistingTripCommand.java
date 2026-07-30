package org.opentripplanner.updater.trip.model;

import java.util.List;
import org.opentripplanner.updater.trip.policy.FormatPolicy;

/**
 * Common interface for the commands changing an existing scheduled trip.
 * <p>
 * Used by {@link org.opentripplanner.updater.trip.ExistingTripChangeFactory} for both
 * UPDATE_EXISTING ({@link ReviseTrip}) and MODIFY_TRIP ({@link ModifyTrip}).
 */
public sealed interface ExistingTripCommand
  extends TripUpdateCommand
  permits ReviseTrip, ModifyTrip {
  List<ParsedStopTimeUpdate> stopTimeUpdates();

  FormatPolicy formatPolicy();

  /**
   * Returns true if any stop time update has an explicit stop sequence number.
   * GTFS-RT updates typically have stop sequences, while SIRI updates do not.
   */
  default boolean hasStopSequences() {
    return stopTimeUpdates()
      .stream()
      .anyMatch(u -> u.stopSequence() != null);
  }
}
