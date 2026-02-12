package org.opentripplanner.updater.trip.handlers;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;
import org.opentripplanner.transit.model.framework.DataValidationException;
import org.opentripplanner.transit.model.framework.DeduplicatorService;
import org.opentripplanner.transit.model.framework.Result;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.site.StopLocation;
import org.opentripplanner.transit.model.timetable.RealTimeState;
import org.opentripplanner.transit.model.timetable.RealTimeTripUpdate;
import org.opentripplanner.transit.model.timetable.Trip;
import org.opentripplanner.transit.model.timetable.TripTimesFactory;
import org.opentripplanner.transit.service.TransitEditorService;
import org.opentripplanner.updater.spi.DataValidationExceptionMapper;
import org.opentripplanner.updater.spi.UpdateError;
import org.opentripplanner.updater.trip.TimetableSnapshotManager;
import org.opentripplanner.updater.trip.model.ResolvedExistingTrip;
import org.opentripplanner.updater.trip.model.ResolvedStopTimeUpdate;
import org.opentripplanner.updater.trip.model.StopReplacementConstraint;
import org.opentripplanner.updater.trip.patterncache.TripPatternCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles trip modification updates (replacing a trip with a modified pattern).
 * <p>
 * This handler supports two use cases:
 * <ul>
 *   <li><b>GTFS-RT REPLACEMENT</b>: Complete stop pattern replacement with full freedom</li>
 *   <li><b>SIRI-ET EXTRA_CALL</b>: Insert extra stops, non-extra stops must match original</li>
 * </ul>
 * <p>
 * This handler receives a {@link ResolvedExistingTrip} with trip, pattern, and service date
 * already resolved by {@link org.opentripplanner.updater.trip.ExistingTripResolver}.
 */
public class ModifyTripHandler implements TripUpdateHandler.ForExistingTrip {

  private static final Logger LOG = LoggerFactory.getLogger(ModifyTripHandler.class);

  @Nullable
  private final TimetableSnapshotManager snapshotManager;

  private final TransitEditorService transitService;
  private final DeduplicatorService deduplicator;
  private final TripPatternCache tripPatternCache;

  public ModifyTripHandler(
    @Nullable TimetableSnapshotManager snapshotManager,
    TransitEditorService transitService,
    DeduplicatorService deduplicator,
    TripPatternCache tripPatternCache
  ) {
    this.snapshotManager = snapshotManager;
    this.transitService = Objects.requireNonNull(transitService);
    this.deduplicator = Objects.requireNonNull(deduplicator);
    this.tripPatternCache = Objects.requireNonNull(tripPatternCache);
  }

  @Override
  public Result<TripUpdateResult, UpdateError> handle(ResolvedExistingTrip resolvedUpdate) {
    // All resolution already done by ExistingTripResolver
    Trip trip = resolvedUpdate.trip();
    TripPattern scheduledPattern = resolvedUpdate.scheduledPattern();
    LocalDate serviceDate = resolvedUpdate.serviceDate();

    LOG.debug(
      "Modifying trip {} on pattern {} for date {}",
      trip.getId(),
      scheduledPattern.getId(),
      serviceDate
    );

    // Validate minimum stops
    var stopTimeUpdates = resolvedUpdate.stopTimeUpdates();
    if (stopTimeUpdates.size() < 2) {
      LOG.debug("MODIFY_TRIP: trip {} has fewer than 2 stops, skipping.", trip.getId());
      return Result.failure(
        new UpdateError(trip.getId(), UpdateError.UpdateErrorType.TOO_FEW_STOPS)
      );
    }

    // Check if this is a SIRI extra call (has isExtraCall flags)
    boolean hasSiriExtraCalls = stopTimeUpdates
      .stream()
      .anyMatch(ResolvedStopTimeUpdate::isExtraCall);

    // Validate SIRI extra call constraints
    if (hasSiriExtraCalls) {
      var validationResult = validateSiriExtraCalls(
        stopTimeUpdates,
        scheduledPattern,
        trip,
        resolvedUpdate.options().stopReplacementConstraint()
      );
      if (validationResult.isFailure()) {
        return Result.failure(validationResult.failureValue());
      }
    }

    // Build the new stop pattern from stop time updates
    var stopPatternResult = HandlerUtils.buildNewStopPattern(
      trip,
      stopTimeUpdates,
      resolvedUpdate.options().firstLastStopTimeAdjustment()
    );
    if (stopPatternResult.isFailure()) {
      return Result.failure(stopPatternResult.failureValue());
    }
    var stopTimesAndPattern = stopPatternResult.successValue();

    // Revert any previous modifications
    if (snapshotManager != null) {
      snapshotManager.revertTripToScheduledTripPattern(trip.getId(), serviceDate);
    }

    // Create scheduled trip times for the new pattern (used as baseline for real-time)
    var scheduledTripTimes = TripTimesFactory.tripTimes(
      trip,
      stopTimesAndPattern.stopTimes(),
      deduplicator
    ).withServiceCode(transitService.getServiceCode(trip.getServiceId()));

    // Validate scheduled times
    try {
      scheduledTripTimes.validateNonIncreasingTimes();
    } catch (DataValidationException e) {
      LOG.info("Invalid scheduled times for modified trip {}: {}", trip.getId(), e.getMessage());
      return DataValidationExceptionMapper.toResult(e);
    }

    // Create the new pattern - don't add scheduled times, only real-time times will be added
    TripPattern newPattern = TripPattern.of(tripPatternCache.generatePatternId(trip))
      .withRoute(trip.getRoute())
      .withMode(trip.getMode())
      .withNetexSubmode(trip.getNetexSubMode())
      .withStopPattern(stopTimesAndPattern.stopPattern())
      .withRealTimeStopPatternModified()
      .withOriginalTripPattern(scheduledPattern)
      .build();

    // Create real-time trip times builder from scheduled
    var builder = scheduledTripTimes.createRealTimeFromScheduledTimes();

    // Apply real-time updates
    HandlerUtils.applyRealTimeUpdates(resolvedUpdate.tripCreationInfo(), builder, stopTimeUpdates);

    // Set state to MODIFIED
    builder.withRealTimeState(RealTimeState.MODIFIED);

    // Mark the original trip as deleted in the scheduled pattern
    HandlerUtils.markScheduledTripAsDeleted(trip, scheduledPattern, serviceDate, snapshotManager);

    // Build and return the result
    try {
      var realTimeTripUpdate = new RealTimeTripUpdate(newPattern, builder.build(), serviceDate);
      LOG.debug(
        "Modified trip {} on {} with new pattern {}",
        trip.getId(),
        serviceDate,
        newPattern.getId()
      );
      return Result.success(new TripUpdateResult(realTimeTripUpdate));
    } catch (DataValidationException e) {
      LOG.info("Invalid real-time data for modified trip {}: {}", trip.getId(), e.getMessage());
      return DataValidationExceptionMapper.toResult(e);
    }
  }

  /**
   * Validate SIRI extra call constraints.
   * Non-extra stops must match the original pattern according to the stop replacement constraint.
   */
  private Result<Void, UpdateError> validateSiriExtraCalls(
    List<ResolvedStopTimeUpdate> stopTimeUpdates,
    TripPattern originalPattern,
    Trip trip,
    StopReplacementConstraint constraint
  ) {
    // Count non-extra stops
    long nonExtraCount = stopTimeUpdates
      .stream()
      .filter(u -> !u.isExtraCall())
      .count();
    if (nonExtraCount != originalPattern.numberOfStops()) {
      LOG.debug(
        "SIRI extra call validation failed: {} non-extra stops but original pattern has {} stops",
        nonExtraCount,
        originalPattern.numberOfStops()
      );
      return Result.failure(
        new UpdateError(trip.getId(), UpdateError.UpdateErrorType.INVALID_STOP_SEQUENCE)
      );
    }

    var validator = new StopReplacementValidator();

    // Validate each non-extra stop matches the original pattern
    int originalIndex = 0;
    for (int i = 0; i < stopTimeUpdates.size(); i++) {
      var stopUpdate = stopTimeUpdates.get(i);
      if (stopUpdate.isExtraCall()) {
        continue;
      }

      StopLocation updateStop = stopUpdate.stop();
      if (updateStop == null) {
        return Result.failure(
          new UpdateError(trip.getId(), UpdateError.UpdateErrorType.UNKNOWN_STOP, i)
        );
      }

      StopLocation originalStop = originalPattern.getStop(originalIndex);

      // Use the configured stop replacement constraint for validation
      var validationResult = validator.validate(originalStop, updateStop, constraint);
      if (validationResult != StopReplacementValidator.Result.VALID) {
        LOG.debug(
          "SIRI extra call validation failed: stop {} at index {} doesn't match original stop {} ({})",
          updateStop.getId(),
          i,
          originalStop.getId(),
          validationResult
        );
        return Result.failure(
          new UpdateError(trip.getId(), UpdateError.UpdateErrorType.STOP_MISMATCH, i)
        );
      }

      originalIndex++;
    }

    return Result.success(null);
  }
}
