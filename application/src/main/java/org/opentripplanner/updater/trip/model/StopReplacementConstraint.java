package org.opentripplanner.updater.trip.model;

/**
 * Defines constraints on which stops can replace scheduled stops when modifying a trip's stop pattern.
 * Different real-time feed formats have different semantics for stop replacements.
 */
public enum StopReplacementConstraint {
  /**
   * No constraint on stop replacement. Replacement stops can be any stop in the system.
   * This is the GTFS-RT semantics where replacement trips can have completely different stop patterns.
   */
  ANY_STOP,

  /**
   * Replacement stops must be within the same parent station as the original stop.
   * This is the SIRI-ET semantics where extra calls and quay changes are constrained
   * to the same station (typically platform/quay changes within a station).
   */
  SAME_PARENT_STATION,

  /**
   * Stop pattern modification is not allowed.
   * Use this when the feed format doesn't support modifying stop patterns.
   */
  NOT_ALLOWED,
}
