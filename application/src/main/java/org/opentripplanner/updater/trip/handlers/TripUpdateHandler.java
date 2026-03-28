package org.opentripplanner.updater.trip.handlers;

import org.opentripplanner.updater.spi.UpdateException;
import org.opentripplanner.updater.trip.model.ResolvedExistingTrip;
import org.opentripplanner.updater.trip.model.ResolvedNewTrip;
import org.opentripplanner.updater.trip.model.ResolvedTripRemoval;

/**
 * Handler interfaces for different types of trip updates.
 * <p>
 * Each handler type receives a specific resolved type containing only the data
 * relevant for that handler's operation.
 */
public final class TripUpdateHandler {

  private TripUpdateHandler() {}

  /**
   * Handler for updates to existing scheduled trips.
   * Used for UPDATE_EXISTING and MODIFY_TRIP update types.
   */
  @FunctionalInterface
  public interface ForExistingTrip {
    TripUpdateResult handle(ResolvedExistingTrip resolvedUpdate) throws UpdateException;
  }

  /**
   * Handler for adding new trips or updating previously added trips.
   * Used for ADD_NEW_TRIP update type.
   */
  @FunctionalInterface
  public interface ForNewTrip {
    TripUpdateResult handle(ResolvedNewTrip resolvedUpdate) throws UpdateException;
  }

  /**
   * Handler for cancelling or deleting trips.
   * Used for CANCEL_TRIP and DELETE_TRIP update types.
   */
  @FunctionalInterface
  public interface ForTripRemoval {
    TripUpdateResult handle(ResolvedTripRemoval resolvedUpdate) throws UpdateException;
  }
}
