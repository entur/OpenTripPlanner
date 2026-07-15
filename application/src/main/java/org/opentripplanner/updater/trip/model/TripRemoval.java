package org.opentripplanner.updater.trip.model;

/**
 * Common interface for trip removal updates (CANCEL_TRIP and DELETE_TRIP).
 * <p>
 * These updates carry only the trip reference and service date — no stop time updates
 * or processing options are needed since the entire trip is being removed.
 * <p>
 * Used by {@link org.opentripplanner.updater.trip.TripRemovalResolver}.
 */
public sealed interface TripRemoval
  extends ParsedTripUpdate
  permits TripCancellation, TripDeletion {}
