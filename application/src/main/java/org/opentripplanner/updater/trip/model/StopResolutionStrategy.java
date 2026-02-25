package org.opentripplanner.updater.trip.model;

/**
 * Defines how a stop reference should be resolved to a stop location.
 */
public enum StopResolutionStrategy {
  /**
   * Direct lookup by stop ID (GTFS-RT behavior).
   * The stop ID is looked up directly in the transit service.
   */
  DIRECT,

  /**
   * Try scheduled stop point mapping first, then fall back to direct (SIRI behavior).
   * First attempts to find the stop via the scheduled stop point mapping (NeTEx),
   * and if not found, falls back to direct stop lookup.
   */
  SCHEDULED_STOP_POINT_FIRST,
}
