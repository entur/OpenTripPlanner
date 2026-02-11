package org.opentripplanner.updater.trip.handlers;

import java.time.LocalDate;
import java.util.List;
import javax.annotation.Nullable;
import org.opentripplanner.transit.model.framework.DataValidationException;
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
import org.opentripplanner.updater.trip.FuzzyTripMatcher;
import org.opentripplanner.updater.trip.StopResolver;
import org.opentripplanner.updater.trip.TripAndPattern;
import org.opentripplanner.updater.trip.TripUpdateApplierContext;
import org.opentripplanner.updater.trip.model.ParsedStopTimeUpdate;
import org.opentripplanner.updater.trip.model.ParsedTripUpdate;
import org.opentripplanner.updater.trip.model.StopReplacementConstraint;
import org.opentripplanner.updater.trip.model.TripReference;
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
 */
public class ModifyTripHandler implements TripUpdateHandler {

  private static final Logger LOG = LoggerFactory.getLogger(ModifyTripHandler.class);

  @Override
  public Result<TripUpdateResult, UpdateError> handle(
    ParsedTripUpdate parsedUpdate,
    TripUpdateApplierContext context,
    TransitEditorService transitService
  ) {
    var tripResolver = context.tripResolver();

    // Resolve service date
    var serviceDateResult = context.serviceDateResolver().resolveServiceDate(parsedUpdate);
    if (serviceDateResult.isFailure()) {
      return Result.failure(serviceDateResult.failureValue());
    }
    var serviceDate = serviceDateResult.successValue();

    // Resolve the trip and pattern
    var tripAndPatternResult = resolveTripWithPattern(
      parsedUpdate,
      serviceDate,
      tripResolver,
      context.fuzzyTripMatcher(),
      transitService
    );
    if (tripAndPatternResult.isFailure()) {
      LOG.debug("Could not resolve trip for modification: {}", parsedUpdate.tripReference());
      return Result.failure(tripAndPatternResult.failureValue());
    }

    var tripAndPattern = tripAndPatternResult.successValue();
    Trip trip = tripAndPattern.trip();
    TripPattern foundPattern = tripAndPattern.tripPattern();

    // Get the scheduled pattern (if modified, get the original)
    TripPattern scheduledPattern = foundPattern.isModified()
      ? foundPattern.getOriginalTripPattern()
      : foundPattern;
    LOG.debug(
      "Resolved trip {} on pattern {} for modification",
      trip.getId(),
      scheduledPattern.getId()
    );

    // Validate service date is valid for this trip
    var serviceId = trip.getServiceId();
    var serviceDates = transitService.getCalendarService().getServiceDatesForServiceId(serviceId);
    if (!serviceDates.contains(serviceDate)) {
      LOG.debug(
        "MODIFY_TRIP: trip {} has service date {} for which trip's service is not valid, skipping.",
        trip.getId(),
        serviceDate
      );
      return Result.failure(
        new UpdateError(trip.getId(), UpdateError.UpdateErrorType.NO_SERVICE_ON_DATE)
      );
    }

    // Validate minimum stops
    var stopTimeUpdates = parsedUpdate.stopTimeUpdates();
    if (stopTimeUpdates.size() < 2) {
      LOG.debug("MODIFY_TRIP: trip {} has fewer than 2 stops, skipping.", trip.getId());
      return Result.failure(
        new UpdateError(trip.getId(), UpdateError.UpdateErrorType.TOO_FEW_STOPS)
      );
    }

    // Check if this is a SIRI extra call (has isExtraCall flags)
    boolean hasSiriExtraCalls = stopTimeUpdates
      .stream()
      .anyMatch(ParsedStopTimeUpdate::isExtraCall);

    // Validate SIRI extra call constraints
    if (hasSiriExtraCalls) {
      var validationResult = validateSiriExtraCalls(
        stopTimeUpdates,
        scheduledPattern,
        context.stopResolver(),
        trip,
        parsedUpdate.options().stopReplacementConstraint()
      );
      if (validationResult.isFailure()) {
        return Result.failure(validationResult.failureValue());
      }
    }

    // Build the new stop pattern from stop time updates
    var stopPatternResult = HandlerUtils.buildNewStopPattern(
      trip,
      stopTimeUpdates,
      context.stopResolver(),
      serviceDate,
      context.timeZone(),
      parsedUpdate.options().firstLastStopTimeAdjustment()
    );
    if (stopPatternResult.isFailure()) {
      return Result.failure(stopPatternResult.failureValue());
    }
    var stopTimesAndPattern = stopPatternResult.successValue();

    // Revert any previous modifications
    var snapshotManager = context.snapshotManager();
    if (snapshotManager != null) {
      snapshotManager.revertTripToScheduledTripPattern(trip.getId(), serviceDate);
    }

    // Create scheduled trip times for the new pattern (used as baseline for real-time)
    var scheduledTripTimes = TripTimesFactory.tripTimes(
      trip,
      stopTimesAndPattern.stopTimes(),
      transitService.getDeduplicator()
    ).withServiceCode(transitService.getServiceCode(trip.getServiceId()));

    // Validate scheduled times
    try {
      scheduledTripTimes.validateNonIncreasingTimes();
    } catch (DataValidationException e) {
      LOG.info("Invalid scheduled times for modified trip {}: {}", trip.getId(), e.getMessage());
      return DataValidationExceptionMapper.toResult(e);
    }

    // Create the new pattern - don't add scheduled times, only real-time times will be added
    var tripPatternCache = context.tripPatternCache();
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
    HandlerUtils.applyRealTimeUpdates(
      parsedUpdate,
      builder,
      stopTimeUpdates,
      serviceDate,
      context.timeZone()
    );

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
    List<ParsedStopTimeUpdate> stopTimeUpdates,
    TripPattern originalPattern,
    StopResolver stopResolver,
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

      StopLocation updateStop = stopResolver.resolve(stopUpdate.stopReference());
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

  /**
   * Resolve a Trip and its TripPattern from a ParsedTripUpdate.
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
