package org.opentripplanner.updater.trip.handlers;

import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import org.opentripplanner.model.PickDrop;
import org.opentripplanner.transit.model.framework.DataValidationException;
import org.opentripplanner.transit.model.framework.Result;
import org.opentripplanner.transit.model.network.StopPattern;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.site.StopLocation;
import org.opentripplanner.transit.model.timetable.RealTimeState;
import org.opentripplanner.transit.model.timetable.RealTimeTripUpdate;
import org.opentripplanner.transit.model.timetable.Trip;
import org.opentripplanner.transit.model.timetable.TripTimes;
import org.opentripplanner.transit.service.TransitEditorService;
import org.opentripplanner.updater.spi.DataValidationExceptionMapper;
import org.opentripplanner.updater.spi.UpdateError;
import org.opentripplanner.updater.trip.StopResolver;
import org.opentripplanner.updater.trip.TimetableSnapshotManager;
import org.opentripplanner.updater.trip.TripUpdateApplierContext;
import org.opentripplanner.updater.trip.gtfs.BackwardsDelayInterpolator;
import org.opentripplanner.updater.trip.gtfs.ForwardsDelayInterpolator;
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
    var tripResolver = context.tripResolver();

    // Resolve the trip from the trip reference
    var tripResult = tripResolver.resolveTrip(tripReference);
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
    // If delay propagation is enabled, start with empty times so interpolators can fill them in
    // Otherwise, pre-fill with scheduled times (SIRI-style: all stops have explicit times)
    var options = parsedUpdate.options();
    var builder = options.propagatesDelays()
      ? tripTimes.createRealTimeWithoutScheduledTimes()
      : tripTimes.createRealTimeFromScheduledTimes();

    // Apply stop time updates - now returns PatternModificationResult
    var applyResult = applyStopTimeUpdates(parsedUpdate, builder, pattern, trip, context);
    if (applyResult.isFailure()) {
      return Result.failure(applyResult.failureValue());
    }
    PatternModificationResult modResult = applyResult.successValue();

    // Determine the pattern to use
    TripPattern finalPattern = pattern;
    RealTimeState realTimeState = RealTimeState.UPDATED;

    // If stop pattern was modified, create or get cached modified pattern
    if (modResult.hasPatternChanges()) {
      StopPattern newStopPattern = buildModifiedStopPattern(
        pattern,
        modResult.stopReplacements(),
        modResult.pickupChanges(),
        modResult.dropoffChanges()
      );

      // Check if pattern actually changed (builder deduplicates)
      if (!pattern.getStopPattern().equals(newStopPattern)) {
        var tripPatternCache = context.tripPatternCache();
        // SiriTripPatternCache uses 2-parameter signature (gets original pattern via injected function)
        finalPattern = tripPatternCache.getOrCreateTripPattern(newStopPattern, trip);
        realTimeState = RealTimeState.MODIFIED;

        // Cancel the trip in the scheduled pattern since it's moving to a modified pattern
        // This prevents the trip from appearing in both patterns in the routing data
        markScheduledTripAsDeleted(trip, pattern, serviceDate, snapshotManager);
      }
    }

    // Set real-time state if any updates were applied
    if (modResult.hasAnyUpdates()) {
      builder.withRealTimeState(realTimeState);
    }

    // Create the RealTimeTripUpdate with the correct pattern
    try {
      var realTimeTripUpdate = new RealTimeTripUpdate(finalPattern, builder.build(), serviceDate);
      LOG.debug("Updated trip {} on {} (state: {})", trip.getId(), serviceDate, realTimeState);
      return Result.success(realTimeTripUpdate);
    } catch (DataValidationException e) {
      LOG.info(
        "Invalid real-time data for trip {} - TripTimes failed to validate. {}",
        trip.getId(),
        e.getMessage()
      );
      return DataValidationExceptionMapper.toResult(e);
    }
  }

  /**
   * Result of applying stop time updates, tracking both time updates and pattern modifications.
   */
  private static class PatternModificationResult {

    boolean hasTimeUpdates = false;
    boolean hasCancellations = false;
    final Map<Integer, StopLocation> stopReplacements = new HashMap<>();
    final Map<Integer, PickDrop> pickupChanges = new HashMap<>();
    final Map<Integer, PickDrop> dropoffChanges = new HashMap<>();

    public boolean hasPatternChanges() {
      return (
        !stopReplacements.isEmpty() ||
        !pickupChanges.isEmpty() ||
        !dropoffChanges.isEmpty() ||
        hasCancellations
      );
    }

    public boolean hasAnyUpdates() {
      return hasTimeUpdates || hasCancellations || hasPatternChanges();
    }

    public Map<Integer, StopLocation> stopReplacements() {
      return stopReplacements;
    }

    public Map<Integer, PickDrop> pickupChanges() {
      return pickupChanges;
    }

    public Map<Integer, PickDrop> dropoffChanges() {
      return dropoffChanges;
    }
  }

  /**
   * Apply stop time updates from the parsed update to the builder.
   * @return Result containing PatternModificationResult tracking all changes, or an error if validation fails
   */
  private Result<PatternModificationResult, UpdateError> applyStopTimeUpdates(
    ParsedTripUpdate parsedUpdate,
    org.opentripplanner.transit.model.timetable.RealTimeTripTimesBuilder builder,
    TripPattern pattern,
    Trip trip,
    TripUpdateApplierContext context
  ) {
    var stopTimeUpdates = parsedUpdate.stopTimeUpdates();

    // Validate stop count matches pattern (SIRI-style validation)
    // Only validate for SIRI-style updates where stops are matched by reference (no explicit stop sequence)
    if (!parsedUpdate.hasStopSequences()) {
      // SIRI-style: validate exact match of stop count
      if (stopTimeUpdates.size() < pattern.numberOfStops()) {
        return Result.failure(
          new UpdateError(trip.getId(), UpdateError.UpdateErrorType.TOO_FEW_STOPS)
        );
      }
      if (stopTimeUpdates.size() > pattern.numberOfStops()) {
        return Result.failure(
          new UpdateError(trip.getId(), UpdateError.UpdateErrorType.TOO_MANY_STOPS)
        );
      }
    }

    var result = new PatternModificationResult();
    var constraint = parsedUpdate.options().stopReplacementConstraint();
    var stopResolver = context.stopResolver();
    var stopReplacementValidator = new StopReplacementValidator();

    for (ParsedStopTimeUpdate stopUpdate : parsedUpdate.stopTimeUpdates()) {
      Integer stopSequence = stopUpdate.stopSequence();
      int stopIndex;
      StopLocation resolvedStop = null;

      // Determine stop index and resolve stop if needed
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

        // Resolve stop if assignedStopId is provided (stop replacement)
        if (stopUpdate.stopReference().hasAssignedStopId()) {
          resolvedStop = stopResolver.resolve(
            StopReference.ofStopId(stopUpdate.stopReference().assignedStopId())
          );
        }
      } else {
        // Match by stop reference (SIRI-style)
        var matchResult = matchStopByReference(stopUpdate.stopReference(), pattern, stopResolver);
        if (matchResult == null) {
          // Failed to match - determine if it's an unknown stop or a mismatch
          var resolvedStopForError = stopResolver.resolve(stopUpdate.stopReference());
          if (resolvedStopForError == null) {
            // Stop reference couldn't be resolved at all
            return Result.failure(
              new UpdateError(trip.getId(), UpdateError.UpdateErrorType.UNKNOWN_STOP)
            );
          } else {
            // Stop was resolved but doesn't match any stop in the pattern
            return Result.failure(
              new UpdateError(trip.getId(), UpdateError.UpdateErrorType.STOP_MISMATCH)
            );
          }
        }
        stopIndex = matchResult.stopIndex;
        resolvedStop = matchResult.resolvedStop;
      }

      // Get the scheduled stop from the pattern
      StopLocation scheduledStop = pattern.getStop(stopIndex);

      // Check if we failed to resolve an assigned stop
      if (resolvedStop == null && stopUpdate.stopReference().hasAssignedStopId()) {
        return Result.failure(
          new UpdateError(trip.getId(), UpdateError.UpdateErrorType.UNKNOWN_STOP, stopIndex)
        );
      }

      // Track stop replacements
      boolean hasStopReplacement =
        resolvedStop != null && !resolvedStop.getId().equals(scheduledStop.getId());

      if (hasStopReplacement) {
        // Validate replacement against constraint
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

        // Valid replacement - track it
        result.stopReplacements.put(stopIndex, resolvedStop);
      }

      // Handle skipped/cancelled stops
      if (stopUpdate.isSkipped()) {
        builder.withCanceled(stopIndex);
        result.hasCancellations = true;
        // Record pickup/dropoff changes to NONE for cancelled stops
        // This ensures the stop pattern reflects the cancellation
        result.pickupChanges.put(stopIndex, PickDrop.NONE);
        result.dropoffChanges.put(stopIndex, PickDrop.NONE);
        continue;
      }

      // Track pickup/dropoff changes
      if (stopUpdate.pickup() != null) {
        PickDrop scheduledPickup = pattern.getBoardType(stopIndex);
        if (!stopUpdate.pickup().equals(scheduledPickup)) {
          result.pickupChanges.put(stopIndex, stopUpdate.pickup());
        }
      }

      if (stopUpdate.dropoff() != null) {
        PickDrop scheduledDropoff = pattern.getAlightType(stopIndex);
        if (!stopUpdate.dropoff().equals(scheduledDropoff)) {
          result.dropoffChanges.put(stopIndex, stopUpdate.dropoff());
        }
      }

      // Apply time updates
      boolean hasTimeUpdate = false;

      if (stopUpdate.hasArrivalUpdate()) {
        var arrivalUpdate = stopUpdate.arrivalUpdate();
        int scheduledArrival = builder.getScheduledArrivalTime(stopIndex);
        int newArrivalTime = arrivalUpdate.resolveTime(scheduledArrival);
        builder.withArrivalTime(stopIndex, newArrivalTime);
        hasTimeUpdate = true;
      }

      if (stopUpdate.hasDepartureUpdate()) {
        var departureUpdate = stopUpdate.departureUpdate();
        int scheduledDeparture = builder.getScheduledDepartureTime(stopIndex);
        int newDepartureTime = departureUpdate.resolveTime(scheduledDeparture);
        builder.withDepartureTime(stopIndex, newDepartureTime);
        hasTimeUpdate = true;
      }

      if (hasTimeUpdate) {
        result.hasTimeUpdates = true;
      }

      // Apply stop headsign if provided
      if (stopUpdate.stopHeadsign() != null) {
        builder.withStopHeadsign(stopIndex, stopUpdate.stopHeadsign());
      }

      // Apply stop real-time state flags
      if (stopUpdate.recorded()) {
        builder.withRecorded(stopIndex);
      }

      if (stopUpdate.predictionInaccurate()) {
        builder.withInaccuratePredictions(stopIndex);
      }
    }

    // Apply delay propagation according to feed configuration
    var options = parsedUpdate.options();

    // Forwards delay propagation: propagate delays to subsequent stops
    var forwardsPropagationType = options.forwardsPropagation();
    if (ForwardsDelayInterpolator.getInstance(forwardsPropagationType).interpolateDelay(builder)) {
      LOG.debug("Propagated delays forwards for trip {}", trip.getId());
      result.hasTimeUpdates = true;
    }

    // Backwards delay propagation: fill in times before first update
    var backwardsPropagationType = options.backwardsPropagation();
    var backwardPropagationIndex = BackwardsDelayInterpolator.getInstance(
      backwardsPropagationType
    ).propagateBackwards(builder);
    backwardPropagationIndex.ifPresent(index ->
      LOG.debug("Propagated delay from stop index {} backwards for trip {}", index, trip.getId())
    );

    // Fallback: If there are still missing times after propagation, copy from scheduled timetable
    // This handles cases where no propagation is configured or updates don't cover all stops
    if (builder.copyMissingTimesFromScheduledTimetable()) {
      LOG.trace("Copied remaining scheduled times for trip {}", trip.getId());
    }

    return Result.success(result);
  }

  /**
   * Build a modified stop pattern based on detected changes.
   * Uses StopPattern.StopPatternBuilder to apply stop replacements and pickup/dropoff changes.
   * The builder automatically deduplicates - if no actual changes, returns the original pattern.
   *
   * @param originalPattern The original scheduled pattern
   * @param stopReplacements Map of stop index to replacement stop
   * @param pickupChanges Map of stop index to new pickup type
   * @param dropoffChanges Map of stop index to new dropoff type
   * @return The modified stop pattern (may be same as original if builder deduplicates)
   */
  private StopPattern buildModifiedStopPattern(
    TripPattern originalPattern,
    Map<Integer, StopLocation> stopReplacements,
    Map<Integer, PickDrop> pickupChanges,
    Map<Integer, PickDrop> dropoffChanges
  ) {
    var builder = originalPattern.copyPlannedStopPattern();

    // Apply stop replacements
    if (!stopReplacements.isEmpty()) {
      builder.replaceStops(stopReplacements);
    }

    // Apply pickup changes
    if (!pickupChanges.isEmpty()) {
      builder.updatePickups(pickupChanges);
    }

    // Apply dropoff changes
    if (!dropoffChanges.isEmpty()) {
      builder.updateDropoffs(dropoffChanges);
    }

    return builder.build();
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

  /**
   * Mark the scheduled trip in the buffer as deleted when moving to a modified pattern.
   * This prevents the trip from appearing in both the scheduled and modified patterns.
   *
   * @param trip The trip to delete from the scheduled pattern
   * @param scheduledPattern The scheduled pattern containing the trip
   * @param serviceDate The service date
   * @param snapshotManager The snapshot manager to update
   */
  private static void markScheduledTripAsDeleted(
    Trip trip,
    TripPattern scheduledPattern,
    java.time.LocalDate serviceDate,
    TimetableSnapshotManager snapshotManager
  ) {
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
}
