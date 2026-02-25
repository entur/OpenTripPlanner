package org.opentripplanner.updater.trip.model;

/**
 * Strategy for adjusting arrival/departure times at first and last stops of a trip.
 * <p>
 * This is needed because:
 * <ul>
 *   <li>At the first stop, there's typically no meaningful arrival time (the vehicle originates there)</li>
 *   <li>At the last stop, there's typically no meaningful departure time (the vehicle terminates there)</li>
 * </ul>
 * <p>
 * Different real-time protocols have different conventions:
 * <ul>
 *   <li><b>SIRI-ET</b>: Always adjusts first arrival = departure and last departure = arrival
 *       to avoid negative dwell times and ensure valid trip times</li>
 *   <li><b>GTFS-RT</b>: Uses times as provided in the message without adjustment</li>
 * </ul>
 */
public enum FirstLastStopTimeAdjustment {
  /**
   * Always adjust times at first and last stops:
   * <ul>
   *   <li>First stop: arrival = departure (no dwell time before trip starts)</li>
   *   <li>Last stop: departure = arrival (no dwell time after trip ends)</li>
   * </ul>
   * This is the SIRI-ET convention and prevents negative dwell times.
   */
  ADJUST,

  /**
   * Preserve times as provided in the real-time message.
   * No adjustment is made to first/last stop times.
   * This is the GTFS-RT convention.
   */
  PRESERVE,
}
