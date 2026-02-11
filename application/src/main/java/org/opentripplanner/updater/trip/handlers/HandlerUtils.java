package org.opentripplanner.updater.trip.handlers;

import java.time.LocalDate;
import java.time.ZoneId;
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
import org.opentripplanner.updater.trip.StopResolver;
import org.opentripplanner.updater.trip.TimetableSnapshotManager;
import org.opentripplanner.updater.trip.model.ParsedStopTimeUpdate;
import org.opentripplanner.updater.trip.model.ParsedTripUpdate;
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
   * Build a new stop pattern and stop times from parsed stop time updates.
   * This creates stop times with scheduled times from the updates.
   *
   * @param trip The trip being modified or created
   * @param stopTimeUpdates The parsed stop time updates
   * @param stopResolver Resolver to look up stops
   * @param serviceDate The service date
   * @param timeZone The timezone for time resolution
   * @return Result containing stop times and pattern, or error if stops cannot be resolved
   */
  public static Result<StopTimesAndPattern, UpdateError> buildNewStopPattern(
    Trip trip,
    List<ParsedStopTimeUpdate> stopTimeUpdates,
    StopResolver stopResolver,
    LocalDate serviceDate,
    ZoneId timeZone
  ) {
    var stopTimes = new ArrayList<StopTime>();

    for (int i = 0; i < stopTimeUpdates.size(); i++) {
      var stopUpdate = stopTimeUpdates.get(i);

      // Resolve the stop
      StopLocation stop = stopResolver.resolve(stopUpdate.stopReference());
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

      // Get arrival time - use scheduled time if available, otherwise actual time
      if (stopUpdate.hasArrivalUpdate()) {
        var arrivalUpdate = stopUpdate.arrivalUpdate().resolve(serviceDate, timeZone);
        Integer scheduledTime = arrivalUpdate.scheduledTimeSecondsSinceMidnight();
        // Use scheduled time if available, otherwise resolve from actual
        stopTime.setArrivalTime(
          scheduledTime != null && scheduledTime > 0 ? scheduledTime : arrivalUpdate.resolveTime(0)
        );
      } else if (!isFirstStop) {
        // Propagate from previous stop if no arrival
        var prevStopTime = stopTimes.get(i - 1);
        stopTime.setArrivalTime(prevStopTime.getDepartureTime());
      }

      // Get departure time - use scheduled time if available, otherwise actual time
      if (stopUpdate.hasDepartureUpdate()) {
        var departureUpdate = stopUpdate.departureUpdate().resolve(serviceDate, timeZone);
        Integer scheduledTime = departureUpdate.scheduledTimeSecondsSinceMidnight();
        // Use scheduled time if available, otherwise resolve from actual
        stopTime.setDepartureTime(
          scheduledTime != null && scheduledTime > 0
            ? scheduledTime
            : departureUpdate.resolveTime(0)
        );
      } else if (!isLastStop) {
        // Use arrival time if no departure
        stopTime.setDepartureTime(stopTime.getArrivalTime());
      } else {
        stopTime.setDepartureTime(stopTime.getArrivalTime());
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
   * @param parsedUpdate The parsed trip update (for trip-level headsign)
   * @param builder The builder to apply updates to
   * @param stopTimeUpdates The stop time updates to apply
   * @param serviceDate The service date
   * @param timeZone The timezone for time resolution
   */
  public static void applyRealTimeUpdates(
    ParsedTripUpdate parsedUpdate,
    RealTimeTripTimesBuilder builder,
    List<ParsedStopTimeUpdate> stopTimeUpdates,
    LocalDate serviceDate,
    ZoneId timeZone
  ) {
    // Apply trip-level headsign from trip creation info
    if (
      parsedUpdate.tripCreationInfo() != null && parsedUpdate.tripCreationInfo().headsign() != null
    ) {
      builder.withTripHeadsign(parsedUpdate.tripCreationInfo().headsign());
    }

    for (int i = 0; i < stopTimeUpdates.size(); i++) {
      var stopUpdate = stopTimeUpdates.get(i);

      // Apply time updates
      if (stopUpdate.hasArrivalUpdate()) {
        var arrivalUpdate = stopUpdate.arrivalUpdate().resolve(serviceDate, timeZone);
        int scheduledArrival = builder.getScheduledArrivalTime(i);
        builder.withArrivalTime(i, arrivalUpdate.resolveTime(scheduledArrival));
      }

      if (stopUpdate.hasDepartureUpdate()) {
        var departureUpdate = stopUpdate.departureUpdate().resolve(serviceDate, timeZone);
        int scheduledDeparture = builder.getScheduledDepartureTime(i);
        builder.withDepartureTime(i, departureUpdate.resolveTime(scheduledDeparture));
      }

      // Apply headsign
      if (stopUpdate.stopHeadsign() != null) {
        builder.withStopHeadsign(i, stopUpdate.stopHeadsign());
      }

      // Apply skipped
      if (stopUpdate.isSkipped()) {
        builder.withCanceled(i);
      }

      // Apply recorded flag
      if (stopUpdate.recorded()) {
        builder.withRecorded(i);
      }

      // Apply prediction inaccurate flag
      if (stopUpdate.predictionInaccurate()) {
        builder.withInaccuratePredictions(i);
      }
    }
  }
}
