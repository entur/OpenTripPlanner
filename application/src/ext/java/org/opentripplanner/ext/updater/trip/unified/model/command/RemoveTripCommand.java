package org.opentripplanner.ext.updater.trip.unified.model.command;

/**
 * Common interface for the commands removing a trip (CANCEL_TRIP and DELETE_TRIP).
 * <p>
 * These commands carry the trip reference, the service date and what the message says about the
 * vehicle - no stop time updates or processing options are needed since the entire trip is being
 * removed.
 * <p>
 * Used by {@link org.opentripplanner.ext.updater.trip.unified.factory.TripRemovalFactory}.
 */
public sealed interface RemoveTripCommand
  extends TripUpdateCommand
  permits CancelTrip, DeleteTrip {}
