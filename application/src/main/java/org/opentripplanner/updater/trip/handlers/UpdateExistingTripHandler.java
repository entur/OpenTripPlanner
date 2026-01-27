package org.opentripplanner.updater.trip.handlers;

import javax.annotation.Nullable;
import org.opentripplanner.transit.model.framework.DataValidationException;
import org.opentripplanner.transit.model.framework.Result;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.site.StopLocation;
import org.opentripplanner.transit.model.timetable.RealTimeTripUpdate;
import org.opentripplanner.transit.model.timetable.Trip;
import org.opentripplanner.transit.model.timetable.TripTimes;
import org.opentripplanner.transit.service.TransitEditorService;
import org.opentripplanner.updater.spi.DataValidationExceptionMapper;
import org.opentripplanner.updater.spi.UpdateError;
import org.opentripplanner.updater.trip.StopResolver;
import org.opentripplanner.updater.trip.TripUpdateApplierContext;
import org.opentripplanner.updater.trip.model.ParsedStopTimeUpdate;
import org.opentripplanner.updater.trip.model.ParsedTripUpdate;
import org.opentripplanner.updater.trip.model.StopReference;
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
        new UpdateError(trip.getId(), UpdateError.UpdateErrorType.TRIP_NOT_FOUND)
      );
    }

    // Get the trip times from the scheduled timetable
    TripTimes tripTimes = pattern.getScheduledTimetable().getTripTimes(trip);
    if (tripTimes == null) {
      LOG.warn("No trip times found for trip {} in pattern {}", trip.getId(), pattern.getId());
      return Result.failure(
        new UpdateError(trip.getId(), UpdateError.UpdateErrorType.TRIP_NOT_FOUND_IN_PATTERN)
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
    var applyResult = applyStopTimeUpdates(parsedUpdate, builder, pattern, trip, context);
    if (applyResult.isFailure()) {
      return Result.failure(applyResult.failureValue());
    }
    boolean hasUpdates = applyResult.successValue();

    // Set real-time state to UPDATED if any updates were applied
    if (hasUpdates) {
      builder.withRealTimeState(org.opentripplanner.transit.model.timetable.RealTimeState.UPDATED);
    }

    // Create the RealTimeTripUpdate
    try {
      var realTimeTripUpdate = new RealTimeTripUpdate(pattern, builder.build(), serviceDate);
      LOG.debug("Updated trip {} on {}", trip.getId(), serviceDate);
      return Result.success(realTimeTripUpdate);
    } catch (DataValidationException e) {
      LOG.info(
        "Invalid real-time data for trip {} - TripTimes failed to validate after applying updates. {}",
        trip.getId(),
        e.getMessage()
      );
      return DataValidationExceptionMapper.toResult(e);
    }
  }

  /**
   * Apply stop time updates from the parsed update to the builder.
   * @return Result containing true if any updates were applied, or an error if validation fails
   */
  private Result<Boolean, UpdateError> applyStopTimeUpdates(
    ParsedTripUpdate parsedUpdate,
    org.opentripplanner.transit.model.timetable.RealTimeTripTimesBuilder builder,
    TripPattern pattern,
    Trip trip,
    TripUpdateApplierContext context
  ) {
    boolean hasUpdates = false;
    var constraint = parsedUpdate.options().stopReplacementConstraint();
    var stopResolver = context.stopResolver();
    var stopReplacementValidator = new StopReplacementValidator();

    for (ParsedStopTimeUpdate stopUpdate : parsedUpdate.stopTimeUpdates()) {
      Integer stopSequence = stopUpdate.stopSequence();
      int stopIndex;
      StopLocation resolvedStop = null;

      if (stopSequence != null) {
        stopIndex = stopSequence;
        if (stopIndex < 0 || stopIndex >= pattern.numberOfStops()) {
          LOG.warn(
            "Stop index {} out of bounds for pattern with {} stops",
            stopIndex,
            pattern.numberOfStops()
          );
          continue;
        }
        // When matching by sequence, only resolve assignedStopId for stop replacement validation
        if (stopUpdate.stopReference().hasAssignedStopId()) {
          resolvedStop = stopResolver.resolve(
            StopReference.ofStopId(stopUpdate.stopReference().assignedStopId())
          );
        }
      } else {
        // Try to match by stop reference (SIRI-style matching)
        var matchResult = matchStopByReference(stopUpdate.stopReference(), pattern, stopResolver);
        if (matchResult == null) {
          LOG.debug("Could not match stop update by reference: {}", stopUpdate.stopReference());
          continue;
        }
        stopIndex = matchResult.stopIndex;
        resolvedStop = matchResult.resolvedStop;
      }

      // Get the scheduled stop from the pattern
      StopLocation scheduledStop = pattern.getStop(stopIndex);

      // Check if we have a stop replacement that we couldn't resolve
      if (resolvedStop == null && stopUpdate.stopReference().hasAssignedStopId()) {
        return Result.failure(
          new UpdateError(trip.getId(), UpdateError.UpdateErrorType.UNKNOWN_STOP, stopIndex)
        );
      }

      // Validate stop replacement constraint (if there's a replacement)
      if (resolvedStop != null) {
        var validationResult = stopReplacementValidator.validate(
          scheduledStop,
          resolvedStop,
          constraint
        );
        if (validationResult != StopReplacementValidator.Result.VALID) {
          var errorType = switch (validationResult) {
            case STOP_MISMATCH -> UpdateError.UpdateErrorType.STOP_MISMATCH;
            default -> UpdateError.UpdateErrorType.UNKNOWN;
          };
          return Result.failure(new UpdateError(trip.getId(), errorType, stopIndex));
        }
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

    return Result.success(hasUpdates);
  }

  /**
   * Result of matching a stop update by its stop reference.
   */
  private record StopMatchResult(int stopIndex, StopLocation resolvedStop) {}

  /**
   * Attempts to match a stop update to a position in the pattern by resolving the stop reference.
   *
   * @param stopReference The stop reference from the update
   * @param pattern The trip pattern to match against
   * @param stopResolver Resolver to convert stop references to stops
   * @return The match result with stop index and resolved stop, or null if no match found
   */
  @Nullable
  private StopMatchResult matchStopByReference(
    StopReference stopReference,
    TripPattern pattern,
    StopResolver stopResolver
  ) {
    // Resolve the stop from the reference (stopId or stopPointRef)
    StopLocation resolvedStop = stopResolver.resolve(stopReference);
    if (resolvedStop == null) {
      return null;
    }

    // Find this stop in the pattern
    for (int i = 0; i < pattern.numberOfStops(); i++) {
      StopLocation patternStop = pattern.getStop(i);
      if (patternStop.getId().equals(resolvedStop.getId())) {
        // Exact match - stop is the same
        return new StopMatchResult(i, resolvedStop);
      }
      // Check if they share the same parent station (quay change within station)
      var patternParent = patternStop.getParentStation();
      var resolvedParent = resolvedStop.getParentStation();
      if (
        patternParent != null &&
        resolvedParent != null &&
        patternParent.getId().equals(resolvedParent.getId())
      ) {
        // Same station - this is likely the matching stop (quay change)
        return new StopMatchResult(i, resolvedStop);
      }
    }

    return null;
  }
}
