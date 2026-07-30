package org.opentripplanner.updater.trip.policy;

/**
 * Behaviour when an added trip references an unknown stop. Replaces the format-divergent
 * {@code UnknownStopBehavior} enum.
 */
public interface UnknownStopPolicy {
  /** Whether an unknown stop should fail the update (SIRI-ET) rather than be filtered (GTFS-RT). */
  boolean failOnUnknownStop();

  /** SIRI-ET: fail on unknown stops. */
  UnknownStopPolicy FAIL = () -> true;
  /** GTFS-RT: silently filter unknown stops. */
  UnknownStopPolicy IGNORE = () -> false;
}
