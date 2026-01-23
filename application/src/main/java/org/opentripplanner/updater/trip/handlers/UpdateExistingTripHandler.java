package org.opentripplanner.updater.trip.handlers;

import org.opentripplanner.transit.model.framework.Result;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.timetable.RealTimeTripUpdate;
import org.opentripplanner.transit.model.timetable.Trip;
import org.opentripplanner.transit.model.timetable.TripTimes;
import org.opentripplanner.transit.service.TransitEditorService;
import org.opentripplanner.updater.spi.UpdateError;
import org.opentripplanner.updater.trip.TripUpdateApplierContext;
import org.opentripplanner.updater.trip.model.ParsedStopTimeUpdate;
import org.opentripplanner.updater.trip.model.ParsedTripUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles updates to existing trips (delay updates, time changes).
 * Maps to GTFS-RT SCHEDULED and SIRI-ET regular updates.
 */
public class UpdateExistingTripHandler implements TripUpdateHandler {

  private static final Logger LOG = LoggerFactory.getLogger(UpdateExistingTripHandler.class);

  @Override
  public Result<RealTimeTripUpdate, UpdateError> handle(
    ParsedTripUpdate parsedUpdate,
    TripUpdateApplierContext context,
    TransitEditorService transitService
  ) {
    var tripReference = parsedUpdate.tripReference();
    var serviceDate = parsedUpdate.serviceDate();
    var tripIdResolver = context.tripIdResolver();

    // Resolve the trip from the trip reference
    var tripResult = tripIdResolver.resolveTrip(tripReference);
    if (tripResult.isFailure()) {
      LOG.debug("Could not resolve trip for update: {}", tripReference);
      return Result.failure(tripResult.failureValue());
    }

    Trip trip = tripResult.successValue();
    LOG.debug("Resolved trip {} for update", trip.getId());

    // Find the pattern for this trip on this service date
    TripPattern pattern = transitService.findPattern(trip, serviceDate);
    if (pattern == null) {
      LOG.warn("No pattern found for trip {} on {}", trip.getId(), serviceDate);
      return Result.failure(
        new UpdateError(trip.getId(), UpdateError.UpdateErrorType.NO_TRIP_FOR_CANCELLATION_FOUND)
      );
    }

    // Get the trip times from the scheduled timetable
    TripTimes tripTimes = pattern.getScheduledTimetable().getTripTimes(trip);
    if (tripTimes == null) {
      LOG.warn("No trip times found for trip {} in pattern {}", trip.getId(), pattern.getId());
      return Result.failure(
        new UpdateError(trip.getId(), UpdateError.UpdateErrorType.NO_TRIP_FOR_CANCELLATION_FOUND)
      );
    }

    // Revert any previous real-time modifications to this trip on this service date
    var snapshotManager = context.snapshotManager();
    if (snapshotManager != null) {
      snapshotManager.revertTripToScheduledTripPattern(trip.getId(), serviceDate);
    }

    // Create the builder from scheduled times
    var builder = tripTimes.createRealTimeFromScheduledTimes();

    // Apply stop time updates and track if any were applied
    boolean hasUpdates = applyStopTimeUpdates(parsedUpdate, builder, pattern);

    // Set real-time state to UPDATED if any updates were applied
    if (hasUpdates) {
      builder.withRealTimeState(org.opentripplanner.transit.model.timetable.RealTimeState.UPDATED);
    }

    // Create the RealTimeTripUpdate
    var realTimeTripUpdate = new RealTimeTripUpdate(pattern, builder.build(), serviceDate);

    LOG.debug("Updated trip {} on {}", trip.getId(), serviceDate);

    return Result.success(realTimeTripUpdate);
  }

  /**
   * Apply stop time updates from the parsed update to the builder.
   * @return true if any updates were applied
   */
  private boolean applyStopTimeUpdates(
    ParsedTripUpdate parsedUpdate,
    org.opentripplanner.transit.model.timetable.RealTimeTripTimesBuilder builder,
    TripPattern pattern
  ) {
    boolean hasUpdates = false;

    for (ParsedStopTimeUpdate stopUpdate : parsedUpdate.stopTimeUpdates()) {
      Integer stopSequence = stopUpdate.stopSequence();
      if (stopSequence == null) {
        LOG.debug(
          "Stop update without stop sequence, trying to match by stop reference: {}",
          stopUpdate.stopReference()
        );
        continue;
      }

      int stopIndex = stopSequence;
      if (stopIndex < 0 || stopIndex >= pattern.numberOfStops()) {
        LOG.warn(
          "Stop index {} out of bounds for pattern with {} stops",
          stopIndex,
          pattern.numberOfStops()
        );
        continue;
      }

      // Handle skipped/cancelled stops
      if (stopUpdate.isSkipped()) {
        builder.withCanceled(stopIndex);
        hasUpdates = true;
        continue;
      }

      // Apply arrival update
      if (stopUpdate.hasArrivalUpdate()) {
        var arrivalUpdate = stopUpdate.arrivalUpdate();
        int scheduledArrival = builder.getScheduledArrivalTime(stopIndex);
        int newArrivalTime = arrivalUpdate.resolveTime(scheduledArrival);
        builder.withArrivalTime(stopIndex, newArrivalTime);
        hasUpdates = true;
      }

      // Apply departure update
      if (stopUpdate.hasDepartureUpdate()) {
        var departureUpdate = stopUpdate.departureUpdate();
        int scheduledDeparture = builder.getScheduledDepartureTime(stopIndex);
        int newDepartureTime = departureUpdate.resolveTime(scheduledDeparture);
        builder.withDepartureTime(stopIndex, newDepartureTime);
        hasUpdates = true;
      }

      // Apply stop real-time state flags
      if (stopUpdate.recorded()) {
        builder.withRecorded(stopIndex);
        hasUpdates = true;
      }

      if (stopUpdate.predictionInaccurate()) {
        builder.withInaccuratePredictions(stopIndex);
        hasUpdates = true;
      }
    }

    return hasUpdates;
  }
}
