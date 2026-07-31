package org.opentripplanner.updater.trip.model.command;

/**
 * Common interface for the commands removing a trip (CANCEL_TRIP and DELETE_TRIP).
 * <p>
 * These commands carry only the trip reference and service date — no stop time updates
 * or processing options are needed since the entire trip is being removed.
 * <p>
 * Used by {@link org.opentripplanner.updater.trip.factory.TripRemovalFactory}.
 */
public sealed interface RemoveTripCommand
  extends TripUpdateCommand
  permits CancelTrip, DeleteTrip {}
