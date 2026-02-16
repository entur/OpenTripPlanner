package org.opentripplanner.updater.trip.handlers;

import java.time.LocalDate;
import java.util.Objects;
import org.opentripplanner.transit.model.framework.DataValidationException;
import org.opentripplanner.transit.model.framework.DeduplicatorService;
import org.opentripplanner.transit.model.framework.Result;
import org.opentripplanner.transit.model.network.TripPattern;
import org.opentripplanner.transit.model.timetable.RealTimeState;
import org.opentripplanner.transit.model.timetable.RealTimeTripUpdate;
import org.opentripplanner.transit.model.timetable.Trip;
import org.opentripplanner.transit.model.timetable.TripTimesFactory;
import org.opentripplanner.transit.service.TransitEditorService;
import org.opentripplanner.updater.spi.DataValidationExceptionMapper;
import org.opentripplanner.updater.spi.UpdateError;
import org.opentripplanner.updater.trip.model.ResolvedExistingTrip;
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

  private final TransitEditorService transitService;
  private final DeduplicatorService deduplicator;
  private final TripPatternCache tripPatternCache;

  public ModifyTripHandler(
    TransitEditorService transitService,
    DeduplicatorService deduplicator,
    TripPatternCache tripPatternCache
  ) {
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

    var stopTimeUpdates = resolvedUpdate.stopTimeUpdates();

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

    // Build and return the result with revert and deletion signals
    try {
      var realTimeTripUpdate = new RealTimeTripUpdate(
        newPattern,
        builder.build(),
        serviceDate,
        null,
        false,
        false,
        resolvedUpdate.dataSource(),
        true,
        scheduledPattern
      );
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
}
