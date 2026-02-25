package org.opentripplanner.updater.trip.model;

/**
 * Strategy for determining when a pickup/dropoff change should trigger a pattern modification.
 * Different real-time feed types have different semantics for boarding activities.
 */
public enum PickDropChangeStrategy {
  /**
   * Exact comparison — any difference in PickDrop value triggers a pattern change.
   * Used for GTFS-RT.
   */
  EXACT_MATCH,

  /**
   * Only routability changes (routable ↔ non-routable) trigger pattern changes.
   * When the parsed value is routable and the scheduled value is also routable,
   * the scheduled value is preserved (e.g. COORDINATE_WITH_DRIVER is not overridden
   * by SCHEDULED just because SIRI says BOARDING/ALIGHTING).
   * Used for SIRI-ET.
   */
  ROUTABILITY_CHANGE_ONLY,
}
