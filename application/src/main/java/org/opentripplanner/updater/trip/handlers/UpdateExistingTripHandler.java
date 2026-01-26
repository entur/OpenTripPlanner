package org.opentripplanner.updater.trip.handlers;

import java.util.Optional;
import javax.annotation.Nullable;
import org.opentripplanner.core.model.id.FeedScopedId;
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
import org.opentripplanner.updater.trip.model.StopReplacementConstraint;
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

      // Validate stop replacement constraint
      var validationError = validateStopReplacement(
        stopUpdate.stopReference(),
        scheduledStop,
        resolvedStop,
        constraint,
        stopResolver,
        trip.getId(),
        stopIndex
      );
      if (validationError.isPresent()) {
        return Result.failure(validationError.get());
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
   * Validates that a stop replacement conforms to the configured constraint.
   *
   * @param stopReference The stop reference from the update (may contain an assignedStopId)
   * @param scheduledStop The scheduled stop from the pattern
   * @param resolvedStop The stop resolved from stopPointRef matching (null if matched by sequence)
   * @param constraint The constraint to enforce
   * @param stopResolver Resolver to look up stop locations
   * @param tripId The trip ID for error reporting
   * @param stopIndex The stop index for error reporting
   * @return Empty if valid, or an UpdateError if the constraint is violated
   */
  private Optional<UpdateError> validateStopReplacement(
    StopReference stopReference,
    StopLocation scheduledStop,
    @Nullable StopLocation resolvedStop,
    StopReplacementConstraint constraint,
    StopResolver stopResolver,
    FeedScopedId tripId,
    int stopIndex
  ) {
    // Determine the actual stop being used in the update
    StopLocation actualStop = null;

    if (resolvedStop != null) {
      // Matched by stopPointRef - the resolved stop is the actual stop
      actualStop = resolvedStop;
    } else if (stopReference.hasAssignedStopId()) {
      // Matched by sequence with an assigned stop - use resolver for consistency
      actualStop = stopResolver.resolve(StopReference.ofStopId(stopReference.assignedStopId()));
    } else {
      // No replacement - using scheduled stop
      return Optional.empty();
    }

    if (actualStop == null) {
      LOG.warn("Could not resolve stop from reference for trip {}", tripId);
      return Optional.of(
        new UpdateError(tripId, UpdateError.UpdateErrorType.UNKNOWN_STOP, stopIndex)
      );
    }

    // If the actual stop is the same as the scheduled stop, no replacement
    if (actualStop.getId().equals(scheduledStop.getId())) {
      return Optional.empty();
    }

    // Now we have a replacement - check constraint
    return validateStopReplacementConstraint(
      scheduledStop,
      actualStop,
      constraint,
      tripId,
      stopIndex
    );
  }

  /**
   * Validates a stop replacement against the configured constraint.
   */
  private Optional<UpdateError> validateStopReplacementConstraint(
    StopLocation scheduledStop,
    StopLocation actualStop,
    StopReplacementConstraint constraint,
    FeedScopedId tripId,
    int stopIndex
  ) {
    switch (constraint) {
      case ANY_STOP -> {
        // Any replacement is allowed
        return Optional.empty();
      }
      case NOT_ALLOWED -> {
        LOG.warn(
          "Stop replacement not allowed: trip {} stop index {} actual {} but scheduled is {}",
          tripId,
          stopIndex,
          actualStop.getId(),
          scheduledStop.getId()
        );
        return Optional.of(
          new UpdateError(tripId, UpdateError.UpdateErrorType.STOP_MISMATCH, stopIndex)
        );
      }
      case SAME_PARENT_STATION -> {
        var scheduledParent = scheduledStop.getParentStation();
        var actualParent = actualStop.getParentStation();

        if (scheduledParent == null || actualParent == null) {
          LOG.warn(
            "Stop replacement rejected: trip {} stop index {} - stops {} and {} are not part of a station",
            tripId,
            stopIndex,
            scheduledStop.getId(),
            actualStop.getId()
          );
          return Optional.of(
            new UpdateError(tripId, UpdateError.UpdateErrorType.STOP_MISMATCH, stopIndex)
          );
        }

        if (!scheduledParent.getId().equals(actualParent.getId())) {
          LOG.warn(
            "Stop replacement rejected: trip {} stop index {} - stop {} (station {}) cannot be replaced by {} (station {})",
            tripId,
            stopIndex,
            scheduledStop.getId(),
            scheduledParent.getId(),
            actualStop.getId(),
            actualParent.getId()
          );
          return Optional.of(
            new UpdateError(tripId, UpdateError.UpdateErrorType.STOP_MISMATCH, stopIndex)
          );
        }

        // Same parent station - replacement is valid
        return Optional.empty();
      }
      default -> {
        return Optional.empty();
      }
    }
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
    // Resolve the stop from the reference
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
