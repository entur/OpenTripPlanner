package org.opentripplanner.updater.trip.handlers;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
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
import org.opentripplanner.updater.spi.DataValidationExceptionMapper;
import org.opentripplanner.updater.spi.UpdateError;
import org.opentripplanner.updater.trip.gtfs.BackwardsDelayInterpolator;
import org.opentripplanner.updater.trip.gtfs.ForwardsDelayInterpolator;
import org.opentripplanner.updater.trip.model.ParsedStopTimeUpdate;
import org.opentripplanner.updater.trip.model.RealTimeStateUpdateStrategy;
import org.opentripplanner.updater.trip.model.ResolvedExistingTrip;
import org.opentripplanner.updater.trip.model.ResolvedStopTimeUpdate;
import org.opentripplanner.updater.trip.model.StopUpdateStrategy;
import org.opentripplanner.updater.trip.patterncache.TripPatternCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles updates to existing trips (delay updates, time changes).
 * Maps to GTFS-RT SCHEDULED and SIRI-ET regular updates.
 * <p>
 * This handler receives a {@link ResolvedExistingTrip} with trip, pattern, and service date
 * already resolved, so it focuses on applying stop time updates and pattern modifications.
 */
public class UpdateExistingTripHandler implements TripUpdateHandler.ForExistingTrip {

  private static final Logger LOG = LoggerFactory.getLogger(UpdateExistingTripHandler.class);

  private final TripPatternCache tripPatternCache;

  public UpdateExistingTripHandler(TripPatternCache tripPatternCache) {
    this.tripPatternCache = Objects.requireNonNull(tripPatternCache);
  }

  @Override
  public Result<TripUpdateResult, UpdateError> handle(ResolvedExistingTrip resolvedUpdate) {
    // All resolution already done by ExistingTripResolver
    Trip trip = resolvedUpdate.trip();
    TripPattern pattern = resolvedUpdate.pattern();
    TripPattern scheduledPattern = resolvedUpdate.scheduledPattern();
    TripTimes tripTimes = resolvedUpdate.scheduledTripTimes();
    LocalDate serviceDate = resolvedUpdate.serviceDate();

    LOG.debug(
      "Updating trip {} on pattern {} for date {}",
      trip.getId(),
      pattern.getId(),
      serviceDate
    );

    // Create the builder from scheduled times
    // If delay propagation is enabled, start with empty times so interpolators can fill them in
    // Otherwise, pre-fill with scheduled times (SIRI-style: all stops have explicit times)
    var options = resolvedUpdate.options();
    var builder = options.propagatesDelays()
      ? tripTimes.createRealTimeWithoutScheduledTimes()
      : tripTimes.createRealTimeFromScheduledTimes();

    // If all stops are cancelled, treat as implicit trip-level cancellation (avoid MODIFIED state)
    if (resolvedUpdate.isAllStopsCancelled()) {
      builder.cancelTrip();
      var realTimeTripUpdate = new RealTimeTripUpdate(
        scheduledPattern,
        builder.build(),
        serviceDate,
        null,
        false,
        false,
        resolvedUpdate.dataSource(),
        true,
        null
      );
      LOG.debug(
        "All stops cancelled - trip {} treated as cancelled on {}",
        trip.getId(),
        serviceDate
      );
      return Result.success(new TripUpdateResult(realTimeTripUpdate));
    }

    // Apply stop time updates - returns PatternModificationResult
    var applyResult = applyStopTimeUpdates(resolvedUpdate, builder, pattern, trip);
    if (applyResult.isFailure()) {
      return Result.failure(applyResult.failureValue());
    }
    PatternModificationResult modResult = applyResult.successValue();

    // Determine the pattern to use
    // After reverting, start with the scheduled pattern unless new modifications are needed
    TripPattern finalPattern = scheduledPattern;
    TripPattern patternToDeleteFrom = null;
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
        // TripPatternCache uses 2-parameter signature (gets original pattern via injected function)
        finalPattern = tripPatternCache.getOrCreateTripPattern(newStopPattern, trip);

        // Conditionally set MODIFIED state based on feed type configuration
        // GTFS-RT (ALWAYS_UPDATED): Keep UPDATED (legacy behavior from TripTimesUpdater:222)
        // SIRI-ET (MODIFIED_ON_PATTERN_CHANGE): Set MODIFIED (legacy behavior from ModifiedTripBuilder:150)
        var stateStrategy = resolvedUpdate.options().realTimeStateStrategy();
        if (stateStrategy == RealTimeStateUpdateStrategy.MODIFIED_ON_PATTERN_CHANGE) {
          realTimeState = RealTimeState.MODIFIED;
        }

        // Signal that the trip should be deleted from the scheduled pattern
        patternToDeleteFrom = scheduledPattern;
      }
    }

    // Set real-time state if any updates were applied
    if (modResult.hasAnyUpdates()) {
      builder.withRealTimeState(realTimeState);
    }

    // Create the RealTimeTripUpdate with revert and deletion signals
    try {
      var realTimeTripUpdate = new RealTimeTripUpdate(
        finalPattern,
        builder.build(),
        serviceDate,
        null,
        false,
        false,
        resolvedUpdate.dataSource(),
        true,
        patternToDeleteFrom
      );
      LOG.debug("Updated trip {} on {} (state: {})", trip.getId(), serviceDate, realTimeState);
      return Result.success(new TripUpdateResult(realTimeTripUpdate));
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
   * Match a pre-resolved stop in the pattern by ID lookup.
   * Used for GTFS-RT updates that omit stopSequence but provide stopId.
   *
   * @param stop The pre-resolved stop location
   * @param pattern The trip pattern to search
   * @return The matched stop index, or -1 if no match found
   */
  private int matchStopInPattern(StopLocation stop, TripPattern pattern) {
    // Search through pattern stops to find a match by ID
    for (int i = 0; i < pattern.numberOfStops(); i++) {
      StopLocation patternStop = pattern.getStop(i);

      // Direct match by ID
      if (patternStop.getId().equals(stop.getId())) {
        return i;
      }

      // Parent station match (quay changes)
      if (
        patternStop.getParentStation() != null &&
        stop.getParentStation() != null &&
        patternStop.getParentStation().getId().equals(stop.getParentStation().getId())
      ) {
        return i;
      }
    }

    // No match found
    return -1;
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
   * Apply stop time updates from the resolved update to the builder.
   * @return Result containing PatternModificationResult tracking all changes, or an error if validation fails
   */
  private Result<PatternModificationResult, UpdateError> applyStopTimeUpdates(
    ResolvedExistingTrip resolvedUpdate,
    org.opentripplanner.transit.model.timetable.RealTimeTripTimesBuilder builder,
    TripPattern pattern,
    Trip trip
  ) {
    var stopUpdateStrategy = resolvedUpdate.options().stopUpdateStrategy();

    var result = new PatternModificationResult();
    var constraint = resolvedUpdate.options().stopReplacementConstraint();
    var stopReplacementValidator = new StopReplacementValidator();

    int listIndex = 0;
    for (ResolvedStopTimeUpdate stopUpdate : resolvedUpdate.stopTimeUpdates()) {
      Integer stopSequence = stopUpdate.stopSequence();
      int stopIndex;
      StopLocation resolvedStop = null;

      if (stopUpdateStrategy == StopUpdateStrategy.FULL_UPDATE) {
        // SIRI-ET: position in list IS the position in pattern
        stopIndex = listIndex;

        // Use the pre-resolved stop for validation and replacement
        resolvedStop = stopUpdate.stop();
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

          // Use pre-resolved stop if assignedStopId is provided (stop replacement)
          if (stopUpdate.stopReference().hasAssignedStopId()) {
            resolvedStop = stopUpdate.stop();
          }
        } else {
          // GTFS-RT without stopSequence: lookup stop by ID in pattern
          resolvedStop = stopUpdate.stop();
          if (resolvedStop == null) {
            return Result.failure(
              new UpdateError(trip.getId(), UpdateError.UpdateErrorType.UNKNOWN_STOP)
            );
          }
          int matchIndex = matchStopInPattern(resolvedStop, pattern);
          if (matchIndex < 0) {
            return Result.failure(
              new UpdateError(trip.getId(), UpdateError.UpdateErrorType.STOP_MISMATCH)
            );
          }
          stopIndex = matchIndex;
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

        // For GTFS-RT SKIPPED stops, don't apply time updates - the forward delay
        // interpolator will interpolate times from surrounding stops.
        // For SIRI CANCELLED stops, fall through to apply explicit time updates
        // to avoid NEGATIVE_HOP_TIME errors on delayed trips.
        if (stopUpdate.status() == ParsedStopTimeUpdate.StopUpdateStatus.SKIPPED) {
          continue;
        }
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
      if (stopUpdate.recorded() && !stopUpdate.isSkipped()) {
        builder.withRecorded(stopIndex);
      }

      if (stopUpdate.predictionInaccurate()) {
        builder.withInaccuratePredictions(stopIndex);
      }

      // Apply occupancy
      if (stopUpdate.occupancy() != null) {
        builder.withOccupancyStatus(stopIndex, stopUpdate.occupancy());
      }
    }

    // Apply delay propagation according to feed configuration
    var options = resolvedUpdate.options();

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
}
