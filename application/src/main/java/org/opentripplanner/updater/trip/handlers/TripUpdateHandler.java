package org.opentripplanner.updater.trip.handlers;

import org.opentripplanner.updater.spi.UpdateException;
import org.opentripplanner.updater.trip.model.ResolvedAddedTripUpdate;
import org.opentripplanner.updater.trip.model.ResolvedDuplicateTrip;
import org.opentripplanner.updater.trip.model.ResolvedExistingTrip;
import org.opentripplanner.updater.trip.model.ResolvedTripCreation;
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
   * Handler for creating new trips that do not exist in the transit model.
   * Used for ADD_NEW_TRIP updates resolved to a trip creation.
   */
  @FunctionalInterface
  public interface ForNewTrip {
    TripUpdateResult handle(ResolvedTripCreation resolvedUpdate) throws UpdateException;
  }

  /**
   * Handler for updating previously added real-time trips.
   * Used for ADD_NEW_TRIP updates resolved to an update of an already added trip.
   */
  @FunctionalInterface
  public interface ForAddedTripUpdate {
    TripUpdateResult handle(ResolvedAddedTripUpdate resolvedUpdate) throws UpdateException;
  }

  /**
   * Handler for cancelling or deleting trips.
   * Used for CANCEL_TRIP and DELETE_TRIP update types.
   */
  @FunctionalInterface
  public interface ForTripRemoval {
    TripUpdateResult handle(ResolvedTripRemoval resolvedUpdate) throws UpdateException;
  }

  /**
   * Handler for duplicating an existing scheduled trip at a new start time.
   * Used for DUPLICATE_TRIP update types.
   */
  @FunctionalInterface
  public interface ForDuplicateTrip {
    TripUpdateResult handle(ResolvedDuplicateTrip resolvedUpdate) throws UpdateException;
  }
}
