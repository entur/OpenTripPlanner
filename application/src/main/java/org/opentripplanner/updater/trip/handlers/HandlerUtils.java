package org.opentripplanner.updater.trip.handlers;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.opentripplanner.model.PickDrop;
import org.opentripplanner.model.StopTime;
import org.opentripplanner.transit.model.framework.Result;
import org.opentripplanner.transit.model.network.StopPattern;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.site.StopLocation;
import org.opentripplanner.transit.model.timetable.RealTimeTripTimesBuilder;
import org.opentripplanner.transit.model.timetable.RealTimeTripUpdate;
import org.opentripplanner.transit.model.timetable.Trip;
import org.opentripplanner.updater.spi.UpdateError;
import org.opentripplanner.updater.trip.TimetableSnapshotManager;
import org.opentripplanner.updater.trip.model.FirstLastStopTimeAdjustment;
import org.opentripplanner.updater.trip.model.ResolvedStopTimeUpdate;
import org.opentripplanner.updater.trip.model.TripCreationInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility methods shared between trip update handlers.
 */
public final class HandlerUtils {

  private static final Logger LOG = LoggerFactory.getLogger(HandlerUtils.class);

  /**
   * Result of building a new stop pattern.
   */
  public record StopTimesAndPattern(List<StopTime> stopTimes, StopPattern stopPattern) {}

  private HandlerUtils() {}

  /**
   * Mark the scheduled trip in the buffer as deleted when moving to a modified pattern.
   * This prevents the trip from appearing in both the scheduled and modified patterns.
   *
   * @param trip The trip to delete from the scheduled pattern
   * @param scheduledPattern The scheduled pattern containing the trip
   * @param serviceDate The service date
   * @param snapshotManager The snapshot manager to update
   */
  public static void markScheduledTripAsDeleted(
    Trip trip,
    TripPattern scheduledPattern,
    LocalDate serviceDate,
    TimetableSnapshotManager snapshotManager
  ) {
    if (snapshotManager == null) {
      LOG.debug("No snapshot manager provided, skipping deletion of scheduled trip");
      return;
    }

    // Get the scheduled trip times from the scheduled timetable
    final var scheduledTimetable = scheduledPattern.getScheduledTimetable();
    final var scheduledTripTimes = scheduledTimetable.getTripTimes(trip);

    if (scheduledTripTimes == null) {
      LOG.warn("Could not mark scheduled trip as deleted: {}", trip.getId());
      return;
    }

    // Create a deleted version of the trip times
    final var builder = scheduledTripTimes.createRealTimeFromScheduledTimes();
    builder.deleteTrip();

    // Update the buffer with the deleted trip times in the scheduled pattern
    snapshotManager.updateBuffer(
      new RealTimeTripUpdate(scheduledPattern, builder.build(), serviceDate)
    );

    LOG.debug("Marked scheduled trip {} as deleted on {}", trip.getId(), serviceDate);
  }

  /**
   * Build a new stop pattern and stop times from resolved stop time updates.
   * This creates stop times with scheduled times from the updates.
   *
   * @param trip The trip being modified or created
   * @param stopTimeUpdates The resolved stop time updates (with pre-resolved stops)
   * @param firstLastAdjustment Strategy for adjusting first/last stop times
   * @return Result containing stop times and pattern, or error if stops cannot be resolved
   */
  public static Result<StopTimesAndPattern, UpdateError> buildNewStopPattern(
    Trip trip,
    List<ResolvedStopTimeUpdate> stopTimeUpdates,
    FirstLastStopTimeAdjustment firstLastAdjustment
  ) {
    var stopTimes = new ArrayList<StopTime>();

    for (int i = 0; i < stopTimeUpdates.size(); i++) {
      var stopUpdate = stopTimeUpdates.get(i);

      // Use the pre-resolved stop
      StopLocation stop = stopUpdate.stop();
      if (stop == null) {
        LOG.debug("Unknown stop in pattern: {}", stopUpdate.stopReference());
        return Result.failure(
          new UpdateError(trip.getId(), UpdateError.UpdateErrorType.UNKNOWN_STOP, i)
        );
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

      // Use departure time for first stop, and arrival time for last stop, to avoid negative dwell times
      // This matches StopTimesMapper lines 68-70 - only apply if adjustment strategy is ADJUST
      if (firstLastAdjustment == FirstLastStopTimeAdjustment.ADJUST) {
        if (isFirstStop && stopTime.isDepartureTimeSet()) {
          stopTime.setArrivalTime(stopTime.getDepartureTime());
        }
        if (isLastStop && stopTime.isArrivalTimeSet()) {
          stopTime.setDepartureTime(stopTime.getArrivalTime());
        }
      }

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
    return Result.success(new StopTimesAndPattern(stopTimes, stopPattern));
  }

  /**
   * Apply real-time updates to a trip times builder.
   *
   * @param tripCreationInfo Optional trip creation info (for trip-level headsign)
   * @param builder The builder to apply updates to
   * @param stopTimeUpdates The resolved stop time updates to apply
   */
  public static void applyRealTimeUpdates(
    TripCreationInfo tripCreationInfo,
    RealTimeTripTimesBuilder builder,
    List<ResolvedStopTimeUpdate> stopTimeUpdates
  ) {
    // Apply trip-level headsign from trip creation info
    if (tripCreationInfo != null && tripCreationInfo.headsign() != null) {
      builder.withTripHeadsign(tripCreationInfo.headsign());
    }

    for (int i = 0; i < stopTimeUpdates.size(); i++) {
      stopTimeUpdates.get(i).applyTo(builder, i);
    }
  }
}
