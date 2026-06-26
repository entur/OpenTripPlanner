package org.opentripplanner.updater.trip.handlers;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.opentripplanner.model.PickDrop;
import org.opentripplanner.transit.model.framework.DataValidationException;
import org.opentripplanner.transit.model.network.StopPattern;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.site.StopLocation;
import org.opentripplanner.transit.model.timetable.RealTimeTripUpdate;
import org.opentripplanner.transit.model.timetable.Trip;
import org.opentripplanner.transit.model.timetable.TripTimes;
import org.opentripplanner.updater.spi.DataValidationExceptionMapper;
import org.opentripplanner.updater.spi.UpdateErrorType;
import org.opentripplanner.updater.spi.UpdateException;
import org.opentripplanner.updater.trip.gtfs.BackwardsDelayInterpolator;
import org.opentripplanner.updater.trip.gtfs.ForwardsDelayInterpolator;
import org.opentripplanner.updater.trip.model.ParsedStopTimeUpdate;
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
  public TripUpdateResult handle(ResolvedExistingTrip resolvedUpdate) {
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
      builder.withCanceled();
      var realTimeTripUpdate = RealTimeTripUpdate.of(scheduledPattern, builder.build(), serviceDate)
        .withProducer(resolvedUpdate.dataSource())
        .withRevertPreviousRealTimeUpdates(true)
        .build();
      LOG.debug(
        "All stops cancelled - trip {} treated as cancelled on {}",
        trip.getId(),
        serviceDate
      );
      return new TripUpdateResult(realTimeTripUpdate);
    }

    // Apply stop time updates - returns PatternModificationResult
    PatternModificationResult modResult = applyStopTimeUpdates(
      resolvedUpdate,
      builder,
      scheduledPattern,
      trip
    );

    // Determine the pattern to use
    // After reverting, start with the scheduled pattern unless new modifications are needed
    TripPattern finalPattern = scheduledPattern;
    TripPattern patternToDeleteFrom = null;
    boolean patternChanged = false;

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
        finalPattern = tripPatternCache.getOrCreateTripPattern(newStopPattern, trip);
        patternChanged = true;
        patternToDeleteFrom = scheduledPattern;
      }
    }

    // Set real-time state if any updates were applied. The format's RealTimeStatePolicy decides
    // whether a pattern change is exposed as MODIFIED (SIRI-ET) or UPDATED (GTFS-RT).
    if (modResult.hasAnyUpdates()) {
      resolvedUpdate.formatPolicy().realTimeState().mark(builder, patternChanged);
    }

    // Create the RealTimeTripUpdate with revert and deletion signals
    try {
      var realTimeTripUpdate = RealTimeTripUpdate.of(finalPattern, builder.build(), serviceDate)
        .withProducer(resolvedUpdate.dataSource())
        .withRevertPreviousRealTimeUpdates(true)
        .withHideTripInScheduledPattern(patternToDeleteFrom)
        .build();
      LOG.debug(
        "Updated trip {} on {} (patternChanged: {})",
        trip.getId(),
        serviceDate,
        patternChanged
      );
      return new TripUpdateResult(realTimeTripUpdate);
    } catch (DataValidationException e) {
      LOG.info(
        "Invalid real-time data for trip {} - TripTimes failed to validate. {}",
        trip.getId(),
        e.getMessage()
      );
      throw DataValidationExceptionMapper.map(e);
    }
  }

  /**
   * Match a pre-resolved stop in the pattern by ID lookup, starting from a given index.
   * Used for GTFS-RT updates that omit stopSequence but provide stopId.
   * The startFrom parameter supports circular routes where the same stop appears multiple times.
   *
   * @param stop The pre-resolved stop location
   * @param pattern The trip pattern to search
   * @param startFrom The index to start searching from (inclusive)
   * @return The matched stop index, or -1 if no match found
   */
  private int matchStopInPattern(StopLocation stop, TripPattern pattern, int startFrom) {
    for (int i = startFrom; i < pattern.numberOfStops(); i++) {
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
    boolean hasNoDataUpdates = false;
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
      return hasTimeUpdates || hasCancellations || hasNoDataUpdates || hasPatternChanges();
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
   * @return PatternModificationResult tracking all changes
   * @throws UpdateException if validation fails
   */
  private PatternModificationResult applyStopTimeUpdates(
    ResolvedExistingTrip resolvedUpdate,
    org.opentripplanner.transit.model.timetable.RealTimeTripTimesBuilder builder,
    TripPattern scheduledPattern,
    Trip trip
  ) {
    var stopUpdateStrategy = resolvedUpdate.options().stopUpdateStrategy();

    var result = new PatternModificationResult();
    var constraint = resolvedUpdate.options().stopReplacementConstraint();
    var stopReplacementValidator = new StopReplacementValidator();

    int listIndex = 0;
    int nextStopSearchIndex = 0;
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
          throw UpdateException.of(trip.getId(), UpdateErrorType.UNKNOWN_STOP, stopIndex);
        }
      } else {
        // PARTIAL_UPDATE (GTFS-RT): use stopSequence or lookup by stopId
        if (stopSequence != null) {
          // GTFS-RT with explicit stop sequence
          stopIndex = stopSequence;
          if (stopIndex < 0 || stopIndex >= scheduledPattern.numberOfStops()) {
            LOG.warn(
              "Stop index {} out of bounds for pattern with {} stops",
              stopIndex,
              scheduledPattern.numberOfStops()
            );
            throw UpdateException.of(
              trip.getId(),
              UpdateErrorType.INVALID_STOP_SEQUENCE,
              stopIndex
            );
          }

          // Use pre-resolved stop if assignedStopId is provided (stop replacement)
          if (stopUpdate.stopReference().hasAssignedStopId()) {
            resolvedStop = stopUpdate.stop();
          }
        } else {
          // GTFS-RT without stopSequence: lookup stop by ID in pattern
          resolvedStop = stopUpdate.stop();
          if (resolvedStop == null) {
            throw UpdateException.of(trip.getId(), UpdateErrorType.INVALID_STOP_REFERENCE);
          }
          int matchIndex = matchStopInPattern(resolvedStop, scheduledPattern, nextStopSearchIndex);
          // If not found from current position, try from beginning (supports out-of-order updates)
          if (matchIndex < 0 && nextStopSearchIndex > 0) {
            matchIndex = matchStopInPattern(resolvedStop, scheduledPattern, 0);
          }
          if (matchIndex < 0) {
            throw UpdateException.of(trip.getId(), UpdateErrorType.INVALID_STOP_REFERENCE);
          }
          stopIndex = matchIndex;
          nextStopSearchIndex = matchIndex + 1;
        }
      }

      listIndex++;

      // Get the scheduled stop from the pattern
      StopLocation scheduledStop = scheduledPattern.getStop(stopIndex);

      // Check if we failed to resolve an assigned stop
      if (resolvedStop == null && stopUpdate.stopReference().hasAssignedStopId()) {
        throw UpdateException.of(trip.getId(), UpdateErrorType.UNKNOWN_STOP, stopIndex);
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
            case STOP_MISMATCH -> UpdateErrorType.STOP_MISMATCH;
            default -> UpdateErrorType.UNKNOWN;
          };
          throw UpdateException.of(trip.getId(), errorType, stopIndex);
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
        result.hasNoDataUpdates = true;
        continue;
      }

      // Track pickup/dropoff changes
      var pickDrop = resolvedUpdate.formatPolicy().pickDrop();
      if (stopUpdate.pickup() != null) {
        PickDrop scheduledPickup = scheduledPattern.getBoardType(stopIndex);
        var effectivePickup = pickDrop.effective(stopUpdate.pickup(), scheduledPickup);
        if (effectivePickup != null && !effectivePickup.equals(scheduledPickup)) {
          result.pickupChanges.put(stopIndex, effectivePickup);
        }
      }

      if (stopUpdate.dropoff() != null) {
        PickDrop scheduledDropoff = scheduledPattern.getAlightType(stopIndex);
        var effectiveDropoff = pickDrop.effective(stopUpdate.dropoff(), scheduledDropoff);
        if (effectiveDropoff != null && !effectiveDropoff.equals(scheduledDropoff)) {
          result.dropoffChanges.put(stopIndex, effectiveDropoff);
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
      if (stopUpdate.hasArrived()) {
        builder.withHasArrived(stopIndex, true);
      }
      if (stopUpdate.hasDeparted()) {
        builder.withHasDeparted(stopIndex, true);
      }

      if (stopUpdate.predictionInaccurate() && !stopUpdate.isSkipped()) {
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

    return result;
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
