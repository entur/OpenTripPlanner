package org.opentripplanner.updater.trip.handlers;

import java.util.ArrayList;
import java.util.List;
import org.opentripplanner.model.PickDrop;
import org.opentripplanner.model.StopTime;
import org.opentripplanner.transit.model.network.StopPattern;
import org.opentripplanner.transit.model.site.StopLocation;
import org.opentripplanner.transit.model.timetable.Trip;
import org.opentripplanner.updater.spi.UpdateErrorType;
import org.opentripplanner.updater.spi.UpdateException;
import org.opentripplanner.updater.trip.model.ResolvedStopTimeUpdate;
import org.opentripplanner.updater.trip.policy.FirstLastStopTimePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Builds a new {@link StopPattern} and the matching {@link StopTime}s from resolved stop time
 * updates, for added and modified trips. The first/last stop time adjustment is delegated to the
 * format's {@link FirstLastStopTimePolicy}.
 */
public final class NewStopPatternFactory {

  private static final Logger LOG = LoggerFactory.getLogger(NewStopPatternFactory.class);

  /** Result of building a new stop pattern. */
  public record StopTimesAndPattern(List<StopTime> stopTimes, StopPattern stopPattern) {}

  private NewStopPatternFactory() {}

  /**
   * Build a new stop pattern and stop times from resolved stop time updates.
   * This creates stop times with scheduled times from the updates.
   *
   * @param trip The trip being modified or created
   * @param stopTimeUpdates The resolved stop time updates (with pre-resolved stops)
   * @param firstLastStopTime Policy for adjusting first/last stop times
   * @return stop times and pattern
   * @throws UpdateException if stops cannot be resolved
   */
  public static StopTimesAndPattern buildNewStopPattern(
    Trip trip,
    List<ResolvedStopTimeUpdate> stopTimeUpdates,
    FirstLastStopTimePolicy firstLastStopTime
  ) {
    var stopTimes = new ArrayList<StopTime>();

    for (int i = 0; i < stopTimeUpdates.size(); i++) {
      var stopUpdate = stopTimeUpdates.get(i);

      // Use the pre-resolved stop
      StopLocation stop = stopUpdate.stop();
      if (stop == null) {
        LOG.debug("Unknown stop in pattern: {}", stopUpdate.stopReference());
        throw UpdateException.of(trip.getId(), UpdateErrorType.UNKNOWN_STOP, i);
      }

      // Create stop time
      var stopTime = new StopTime();
      stopTime.setTrip(trip);
      stopTime.setStop(stop);
      stopTime.setStopSequence(i);

      // Resolve times
      boolean isFirstStop = (i == 0);
      boolean isLastStop = (i == stopTimeUpdates.size() - 1);

      // Get departure time first (needed for arrival fallback)
      Integer departureTime = null;
      if (stopUpdate.hasDepartureUpdate()) {
        departureTime = stopUpdate.departureUpdate().resolveScheduledOrFallback();
      }

      // Get arrival time - use scheduled time if available, otherwise fallback to departure
      // This matches StopTimesMapper: aimedArrivalTime ?? aimedDepartureTime
      if (stopUpdate.hasArrivalUpdate()) {
        stopTime.setArrivalTime(stopUpdate.arrivalUpdate().resolveScheduledOrFallback());
      } else if (departureTime != null) {
        // Fallback: use departure time as arrival (matches old StopTimesMapper logic)
        stopTime.setArrivalTime(departureTime);
      } else if (!isFirstStop) {
        // Last resort: propagate from previous stop
        var prevStopTime = stopTimes.get(i - 1);
        stopTime.setArrivalTime(prevStopTime.getDepartureTime());
      }

      // Set departure time
      if (departureTime != null) {
        stopTime.setDepartureTime(departureTime);
      } else if (stopTime.isArrivalTimeSet()) {
        // Fallback: use arrival time as departure (matches old StopTimesMapper logic)
        stopTime.setDepartureTime(stopTime.getArrivalTime());
      }

      // Use departure time for first stop, and arrival time for last stop, to avoid negative dwell
      // times. This matches StopTimesMapper lines 68-70 - only applied for the ADJUST policy.
      firstLastStopTime.adjust(stopTime, isFirstStop, isLastStop);

      // Handle pickup/dropoff
      if (stopUpdate.pickup() != null) {
        stopTime.setPickupType(stopUpdate.pickup());
      } else {
        stopTime.setPickupType(isLastStop ? PickDrop.NONE : PickDrop.SCHEDULED);
      }

      if (stopUpdate.dropoff() != null) {
        stopTime.setDropOffType(stopUpdate.dropoff());
      } else {
        stopTime.setDropOffType(isFirstStop ? PickDrop.NONE : PickDrop.SCHEDULED);
      }

      // Handle headsign
      if (stopUpdate.stopHeadsign() != null) {
        stopTime.setStopHeadsign(stopUpdate.stopHeadsign());
      }

      // Handle skipped stops
      if (stopUpdate.isSkipped()) {
        stopTime.setPickupType(PickDrop.CANCELLED);
        stopTime.setDropOffType(PickDrop.CANCELLED);
      }

      stopTimes.add(stopTime);
    }

    var stopPattern = new StopPattern(stopTimes);
    return new StopTimesAndPattern(stopTimes, stopPattern);
  }
}
