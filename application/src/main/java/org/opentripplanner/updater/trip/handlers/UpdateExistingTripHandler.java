package org.opentripplanner.updater.trip.handlers;

import java.time.LocalDate;
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
import org.opentripplanner.updater.trip.FuzzyTripMatcher;
import org.opentripplanner.updater.trip.StopResolver;
import org.opentripplanner.updater.trip.TimetableSnapshotManager;
import org.opentripplanner.updater.trip.TripAndPattern;
import org.opentripplanner.updater.trip.TripUpdateApplierContext;
import org.opentripplanner.updater.trip.gtfs.BackwardsDelayInterpolator;
import org.opentripplanner.updater.trip.gtfs.ForwardsDelayInterpolator;
import org.opentripplanner.updater.trip.model.ParsedStopTimeUpdate;
import org.opentripplanner.updater.trip.model.ParsedTripUpdate;
import org.opentripplanner.updater.trip.model.RealTimeStateUpdateStrategy;
import org.opentripplanner.updater.trip.model.StopReference;
import org.opentripplanner.updater.trip.model.StopUpdateStrategy;
import org.opentripplanner.updater.trip.model.TripReference;
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
    var tripResolver = context.tripResolver();

    // Resolve service date (from parsedUpdate or from tripOnServiceDateId)
    var serviceDateResult = context.serviceDateResolver().resolveServiceDate(parsedUpdate);
    if (serviceDateResult.isFailure()) {
      return Result.failure(serviceDateResult.failureValue());
    }
    var serviceDate = serviceDateResult.successValue();

    // Resolve the trip and pattern (with optional fuzzy matching fallback)
    var tripAndPatternResult = resolveTripWithPattern(
      parsedUpdate,
      serviceDate,
      tripResolver,
      context.fuzzyTripMatcher(),
      transitService
    );
    if (tripAndPatternResult.isFailure()) {
      LOG.debug("Could not resolve trip for update: {}", parsedUpdate.tripReference());
      return Result.failure(tripAndPatternResult.failureValue());
    }

    var tripAndPattern = tripAndPatternResult.successValue();
    Trip trip = tripAndPattern.trip();
    TripPattern pattern = tripAndPattern.tripPattern();
    LOG.debug("Resolved trip {} on pattern {} for update", trip.getId(), pattern.getId());

    // Validate that the service date is valid for this trip's service
    var serviceId = trip.getServiceId();
    var serviceDates = transitService.getCalendarService().getServiceDatesForServiceId(serviceId);
    if (!serviceDates.contains(serviceDate)) {
      LOG.debug(
        "SCHEDULED trip {} has service date {} for which trip's service is not valid, skipping.",
        trip.getId(),
        serviceDate
      );
      return Result.failure(
        new UpdateError(trip.getId(), UpdateError.UpdateErrorType.NO_SERVICE_ON_DATE)
      );
    }

    // Get the trip times from the scheduled timetable
    // If we're working with a realtime-modified pattern, get trip times from the original pattern
    TripPattern scheduledPattern = pattern.isModified()
      ? pattern.getOriginalTripPattern()
      : pattern;
    TripTimes tripTimes = scheduledPattern.getScheduledTimetable().getTripTimes(trip);
    if (tripTimes == null) {
      LOG.warn(
        "No trip times found for trip {} in pattern {}",
        trip.getId(),
        scheduledPattern.getId()
      );
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
    var applyResult = applyStopTimeUpdates(
      parsedUpdate,
      builder,
      pattern,
      trip,
      context,
      serviceDate
    );
    if (applyResult.isFailure()) {
      return Result.failure(applyResult.failureValue());
    }
    PatternModificationResult modResult = applyResult.successValue();

    // Determine the pattern to use
    // After reverting, start with the scheduled pattern unless new modifications are needed
    TripPattern finalPattern = scheduledPattern;
    RealTimeState realTimeState = RealTimeState.UPDATED;

    // If stop pattern was modified, create or get cached modified pattern
    if (modResult.hasPatternChanges()) {
      StopPattern newStopPattern = buildModifiedStopPattern(
        scheduledPattern,
        modResult.stopReplacements(),
        modResult.pickupChanges(),
        modResult.dropoffChanges()
      );

      // Check if pattern actually changed (builder deduplicates)
      // Compare against the scheduled pattern to determine if we need a modified pattern
      if (!scheduledPattern.getStopPattern().equals(newStopPattern)) {
        var tripPatternCache = context.tripPatternCache();
        // SiriTripPatternCache uses 2-parameter signature (gets original pattern via injected function)
        finalPattern = tripPatternCache.getOrCreateTripPattern(newStopPattern, trip);

        // Conditionally set MODIFIED state based on feed type configuration
        // GTFS-RT (ALWAYS_UPDATED): Keep UPDATED (legacy behavior from TripTimesUpdater:222)
        // SIRI-ET (MODIFIED_ON_PATTERN_CHANGE): Set MODIFIED (legacy behavior from ModifiedTripBuilder:150)
        var stateStrategy = parsedUpdate.options().realTimeStateStrategy();
        if (stateStrategy == RealTimeStateUpdateStrategy.MODIFIED_ON_PATTERN_CHANGE) {
          realTimeState = RealTimeState.MODIFIED;
        }

        // Cancel the trip in the scheduled pattern since it's moving to a modified pattern
        // This prevents the trip from appearing in both patterns in the routing data
        markScheduledTripAsDeleted(trip, scheduledPattern, serviceDate, snapshotManager);
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
   * Result of matching a stop by reference, containing the matched stop index and resolved stop.
   */
  private record StopMatchResult(int stopIndex, StopLocation resolvedStop) {}

  /**
   * Match a stop in the pattern by stop reference (stop ID lookup).
   * Used for GTFS-RT updates that omit stopSequence but provide stopId.
   *
   * @param stopReference The stop reference to match
   * @param pattern The trip pattern to search
   * @param stopResolver The stop resolver to use
   * @return StopMatchResult with matched index and resolved stop, or null if no match found
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

    // Search through pattern stops to find a match by ID
    for (int i = 0; i < pattern.numberOfStops(); i++) {
      StopLocation patternStop = pattern.getStop(i);

      // Direct match by ID
      if (patternStop.getId().equals(resolvedStop.getId())) {
        return new StopMatchResult(i, resolvedStop);
      }

      // Parent station match (quay changes)
      if (
        patternStop.getParentStation() != null &&
        resolvedStop.getParentStation() != null &&
        patternStop.getParentStation().getId().equals(resolvedStop.getParentStation().getId())
      ) {
        return new StopMatchResult(i, resolvedStop);
      }
    }

    // No match found
    return null;
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
    TripUpdateApplierContext context,
    LocalDate serviceDate
  ) {
    var stopUpdateStrategy = parsedUpdate.options().stopUpdateStrategy();
    var stopTimeUpdates = parsedUpdate.stopTimeUpdates();

    // Validate FULL_UPDATE strategy constraints
    if (stopUpdateStrategy == StopUpdateStrategy.FULL_UPDATE) {
      // FULL_UPDATE must not use stopSequence
      if (parsedUpdate.hasStopSequences()) {
        return Result.failure(
          new UpdateError(trip.getId(), UpdateError.UpdateErrorType.INVALID_STOP_SEQUENCE)
        );
      }

      // FULL_UPDATE must have exact stop count match
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

    int listIndex = 0;
    for (ParsedStopTimeUpdate stopUpdate : parsedUpdate.stopTimeUpdates()) {
      Integer stopSequence = stopUpdate.stopSequence();
      int stopIndex;
      StopLocation resolvedStop = null;

      if (stopUpdateStrategy == StopUpdateStrategy.FULL_UPDATE) {
        // SIRI-ET: position in list IS the position in pattern
        stopIndex = listIndex;

        // Resolve the stop from the stop reference for validation and replacement
        resolvedStop = stopResolver.resolve(stopUpdate.stopReference());
        if (resolvedStop == null) {
          return Result.failure(
            new UpdateError(trip.getId(), UpdateError.UpdateErrorType.UNKNOWN_STOP, stopIndex)
          );
        }
      } else {
        // PARTIAL_UPDATE (GTFS-RT): use stopSequence or lookup by stopId
        if (stopSequence != null) {
          // GTFS-RT with explicit stop sequence
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
          // GTFS-RT without stopSequence: lookup stop by ID in pattern
          var matchResult = matchStopByReference(stopUpdate.stopReference(), pattern, stopResolver);
          if (matchResult == null) {
            // Failed to match - determine if unknown stop or mismatch
            var resolvedStopForError = stopResolver.resolve(stopUpdate.stopReference());
            if (resolvedStopForError == null) {
              return Result.failure(
                new UpdateError(trip.getId(), UpdateError.UpdateErrorType.UNKNOWN_STOP)
              );
            } else {
              return Result.failure(
                new UpdateError(trip.getId(), UpdateError.UpdateErrorType.STOP_MISMATCH)
              );
            }
          }
          stopIndex = matchResult.stopIndex;
          resolvedStop = matchResult.resolvedStop;
        }
      }

      listIndex++;

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

        // Track cancelled stops as pickup/dropoff changes for both GTFS-RT and SIRI-ET
        // This ensures pattern modification is detected and matches legacy behavior
        result.pickupChanges.put(stopIndex, PickDrop.CANCELLED);
        result.dropoffChanges.put(stopIndex, PickDrop.CANCELLED);

        continue;
      }

      // Handle NO_DATA stops
      if (stopUpdate.status() == ParsedStopTimeUpdate.StopUpdateStatus.NO_DATA) {
        builder.withNoData(stopIndex);
        // Don't process time updates for NO_DATA stops - they should have none
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
      var timeZone = context.timeZone();

      if (stopUpdate.hasArrivalUpdate()) {
        var parsedArrivalUpdate = stopUpdate.arrivalUpdate();
        var arrivalUpdate = parsedArrivalUpdate.resolve(serviceDate, timeZone);
        int scheduledArrival = builder.getScheduledArrivalTime(stopIndex);
        int newArrivalTime = arrivalUpdate.resolveTime(scheduledArrival);
        builder.withArrivalTime(stopIndex, newArrivalTime);
        hasTimeUpdate = true;
      }

      if (stopUpdate.hasDepartureUpdate()) {
        var parsedDepartureUpdate = stopUpdate.departureUpdate();
        var departureUpdate = parsedDepartureUpdate.resolve(serviceDate, timeZone);
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

  /**
   * Resolve a Trip and its TripPattern from a ParsedTripUpdate.
   * <p>
   * Resolution order:
   * <ol>
   *   <li>Try exact match via TripResolver</li>
   *   <li>If exact match fails AND fuzzy matching is allowed AND a fuzzy matcher is configured,
   *       try fuzzy matching</li>
   * </ol>
   *
   * @param parsedUpdate the parsed update containing trip reference and stop info
   * @param serviceDate the service date for matching
   * @param tripResolver the resolver for exact trip lookups
   * @param fuzzyTripMatcher the fuzzy matcher (may be null)
   * @param transitService the transit service for pattern lookups
   * @return Result containing the matched trip and pattern, or an error if not found
   */
  private Result<TripAndPattern, UpdateError> resolveTripWithPattern(
    ParsedTripUpdate parsedUpdate,
    LocalDate serviceDate,
    org.opentripplanner.updater.trip.TripResolver tripResolver,
    @Nullable FuzzyTripMatcher fuzzyTripMatcher,
    TransitEditorService transitService
  ) {
    TripReference reference = parsedUpdate.tripReference();

    // Try exact match first
    var exactResult = tripResolver.resolveTrip(reference);
    if (exactResult.isSuccess()) {
      Trip trip = exactResult.successValue();
      TripPattern pattern = transitService.findPattern(trip, serviceDate);
      if (pattern == null) {
        pattern = transitService.findPattern(trip);
      }
      if (pattern != null) {
        return Result.success(new TripAndPattern(trip, pattern));
      }
      LOG.warn("Trip {} found but no pattern available", trip.getId());
      return Result.failure(
        new UpdateError(reference.tripId(), UpdateError.UpdateErrorType.TRIP_NOT_FOUND_IN_PATTERN)
      );
    }

    // Exact match failed - try fuzzy matching if allowed
    if (shouldTryFuzzyMatching(reference, fuzzyTripMatcher)) {
      LOG.debug("Exact match failed for {}, trying fuzzy matching", reference);
      return fuzzyTripMatcher.match(reference, parsedUpdate, serviceDate);
    }

    // Return the original exact match error
    return Result.failure(exactResult.failureValue());
  }

  /**
   * Check if fuzzy matching should be attempted for the given reference.
   */
  private boolean shouldTryFuzzyMatching(
    TripReference reference,
    @Nullable FuzzyTripMatcher fuzzyTripMatcher
  ) {
    if (fuzzyTripMatcher == null) {
      return false;
    }
    return reference.fuzzyMatchingHint() == TripReference.FuzzyMatchingHint.FUZZY_MATCH_ALLOWED;
  }
}
