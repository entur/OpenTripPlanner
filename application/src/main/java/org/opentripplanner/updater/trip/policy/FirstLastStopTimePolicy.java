package org.opentripplanner.updater.trip.policy;

import org.opentripplanner.model.StopTime;

/**
 * Adjusts (or preserves) the first/last stop times when building a new stop pattern. Replaces the
 * format-divergent {@code FirstLastStopTimeAdjustment} enum.
 */
public interface FirstLastStopTimePolicy {
  void adjust(StopTime stopTime, boolean isFirstStop, boolean isLastStop);

  /** GTFS-RT: use times as provided. */
  FirstLastStopTimePolicy PRESERVE = (stopTime, isFirstStop, isLastStop) -> {};

  /**
   * SIRI-ET: use departure for the first stop's arrival and arrival for the last stop's departure,
   * to avoid negative dwell times.
   */
  FirstLastStopTimePolicy ADJUST = (stopTime, isFirstStop, isLastStop) -> {
    if (isFirstStop && stopTime.isDepartureTimeSet()) {
      stopTime.setArrivalTime(stopTime.getDepartureTime());
    }
    if (isLastStop && stopTime.isArrivalTimeSet()) {
      stopTime.setDepartureTime(stopTime.getArrivalTime());
    }
  };
}
